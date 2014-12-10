package org.mqnaas.api;

import org.mqnaas.core.api.IApplication;
import org.mqnaas.core.api.ICapability;
import org.mqnaas.core.api.ICoreProvider;
import org.mqnaas.core.api.IExecutionService;
import org.mqnaas.core.api.IObservationService;
import org.mqnaas.core.api.IRootResource;
import org.mqnaas.core.api.IRootResourceAdministration;
import org.mqnaas.core.api.IRootResourceProvider;
import org.mqnaas.core.api.IService;
import org.mqnaas.core.api.IServiceProvider;
import org.mqnaas.core.api.Specification;
import org.mqnaas.core.api.annotations.DependingOn;
import org.mqnaas.core.api.exceptions.ServiceNotFoundException;
import org.mqnaas.core.impl.ApplicationInstance;
import org.mqnaas.core.impl.IBindingManagement;
import org.mqnaas.core.impl.notificationfilter.ServiceFilterWithParams;
import org.mqnaas.core.impl.resourcetree.ApplicationNode;
import org.mqnaas.core.impl.resourcetree.CapabilityNode;
import org.mqnaas.core.impl.resourcetree.ResourceNode;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connector between {@link IBindingManagement} and {@link IRESTAPIProvider}
 * 
 * This class main responsability is the automatic publication of the REST API of each ICapability and IApplication registered in the system.
 * 
 * In order to achieve this goal, this IApplication observes execution of various {@link IBindingManagement} provided services and triggers the
 * execution of appropriated services in {@link IRESTAPIProvider}
 * 
 * This class has the additional responsability of computing a path for each REST endpoint.
 * 
 * 
 * @author Isart Canyameres Gimenez (i2cat)
 *
 */
public class APIConnector implements IAPIConnector {

	private static final Logger	log	= LoggerFactory.getLogger(APIConnector.class);

	@DependingOn
	IObservationService			observationService;

	@DependingOn
	IServiceProvider			serviceProvider;

	@DependingOn
	ICoreProvider				coreProvider;

	@DependingOn
	IRESTAPIProvider			restApiProvider;

	/**
	 * Depending on IBindingManagement forces this application to be activated only once IBindingManagement is available, which is required to get
	 * observed services in activate() method.
	 */
	@DependingOn
	@SuppressWarnings("unused")
	IBindingManagement			bindingManagement;

	@DependingOn
	IExecutionService executionService;

	@DependingOn
	IRootResourceProvider		rootResourceProvider;

	@DependingOn
	IRootResourceAdministration rootResourceAdmin;
	
	private ServiceRegistration<IAPIConnector> osgiRegistration;


	@Override
	public void activate() {

		try {
			/**
			 * {@link IBindingManagement#bind(CapabilityNode, ResourceNode) bind}
			 */
			IService bindService = serviceProvider.getService(coreProvider.getCore(), "bind", CapabilityNode.class, ResourceNode.class);
			/**
			 * {@link IBindingManagement#unbind(CapabilityNode, ResourceNode) unbind}
			 */
			IService unbindService = serviceProvider.getService(coreProvider.getCore(), "unbind", CapabilityNode.class, ResourceNode.class);

			/**
			 * {@link IBindingManagement#addApplicationInstance(ApplicationInstance) addApplicationInstance}
			 */
			IService addApplicationInstance = serviceProvider.getService(coreProvider.getCore(), "addApplicationInstance",
					ApplicationInstance.class);
			/**
			 * {@link IBindingManagement#removeApplicationInstance(ApplicationInstance) removeApplicationInstance}
			 */
			IService removeApplicationInstance = serviceProvider.getService(coreProvider.getCore(), "removeApplicationInstance",
					ApplicationInstance.class);

			IService publishCapabilityService = serviceProvider.getApplicationService(this, "publishCapability", CapabilityNode.class,
					ResourceNode.class);
			IService unpublishCapabilityService = serviceProvider.getApplicationService(this, "unpublishCapability", CapabilityNode.class,
					ResourceNode.class);
			IService publishApplicationService = serviceProvider.getApplicationService(this, "publishApplication", ApplicationInstance.class);
			IService unpublishApplicationService = serviceProvider.getApplicationService(this, "unpublishApplication", ApplicationInstance.class);

			// register publishServices for them being executed after each bind
			observationService.registerObservation(new ServiceFilterWithParams(bindService), publishCapabilityService);
			observationService.registerObservation(new ServiceFilterWithParams(addApplicationInstance), publishApplicationService);

			// register unpublishServices for them being executed after each unbind
			observationService.registerObservation(new ServiceFilterWithParams(unbindService), unpublishCapabilityService);
			observationService.registerObservation(new ServiceFilterWithParams(removeApplicationInstance), unpublishApplicationService);

			// TODO Get already registered capabilities and apps, and publish them. 

		} catch (ServiceNotFoundException e) {
			// TODO treat exception
			log.error("Failed to register APIConnector. REST API will NOT be published automatically.", e);
		}

		// publishing the core manually
		// FIXME to be removed
		try {
			restApiProvider.publish(rootResourceProvider, IRootResourceProvider.class, "/mqnaas/IRootResourceProvider/");
			restApiProvider.publish(rootResourceAdmin, IRootResourceAdministration.class, "/mqnaas/IRootResourceAdministration/");
			restApiProvider.publish(observationService, IObservationService.class, "/mqnaas/IObservationService/");
			restApiProvider.publish(executionService, IExecutionService.class, "/mqnaas/IExecutionService/");
			restApiProvider.publish(serviceProvider, IServiceProvider.class, "/mqnaas/IServiceProvider/");
		} catch (Exception e){
			// TODO treat exception 
			log.error("Failed to register core services API.", e);
		}
		
		registerAsOSGiService();
	}

	@Override
	public void deactivate() {
		
		unregisterAsOSGiService();
		
		// TODO unpublish all

	}

	// ////////
	// Services
	// ////////

	public void publishCapability(CapabilityNode capabilityNode, ResourceNode unused) throws Exception {
		for (Class<? extends ICapability> capabClass : capabilityNode.getContent().getCapabilities()) {
			String uri = getPathForApplication(capabilityNode, capabClass, new StringBuffer()).toString();
			log.debug("Publishing API for interface {} of capability {} with path {}", capabClass.getName(), capabilityNode.getContent()
					.getInstance(), uri);
			restApiProvider.publish((ICapability) capabilityNode.getContent().getInstance(), capabClass, uri);
		}
	}

	public void unpublishCapability(CapabilityNode capabilityNode, ResourceNode boundTo) {
		// TODO Auto-generated method stub
	}

	public void publishApplication(ApplicationInstance applicationInstance) {
		// TODO Auto-generated method stub
	}

	public void unpublishApplication(ApplicationInstance applicationInstance) {
		// TODO Auto-generated method stub
	}

	private StringBuffer getPathForResource(ResourceNode resourceNode, StringBuffer alreadyComputedPath) {

		if (resourceNode == null)
			return alreadyComputedPath;

		if (resourceNode.getContent() instanceof IRootResource &&
				(((IRootResource) resourceNode.getContent()).getDescriptor().getSpecification().getType()).equals(Specification.Type.CORE)) {

			alreadyComputedPath.insert(0, "/mqnaas/");
			return alreadyComputedPath;
		}

		// TODO define a way to get user friendly names for resources
		String resourceName = resourceNode.getContent().getId();
		alreadyComputedPath.insert(0, resourceName + "/");
		return getPathForApplication(resourceNode.getParent(), resourceNode.getParentInterface(), alreadyComputedPath);
	}

	private StringBuffer getPathForApplication(ApplicationNode applicationNode, Class<? extends IApplication> interfaze,
			StringBuffer alreadyComputedPath) {

		if (applicationNode == null)
			return alreadyComputedPath;

		// TODO get applicationName from @Path annotation in interfaze
		String applicationName = interfaze.getSimpleName();

		if (applicationNode instanceof CapabilityNode) {

			CapabilityNode capabilityNode = (CapabilityNode) applicationNode;
			alreadyComputedPath.insert(0, applicationName + "/");
			return getPathForResource(capabilityNode.getParent(), alreadyComputedPath);

		} else {

			alreadyComputedPath.insert(0, "/app/" + applicationName + "/");
			return alreadyComputedPath;
		}
	}
	
	private void registerAsOSGiService() {
		if (FrameworkUtil.getBundle(APIConnector.class) != null) {
			BundleContext context = FrameworkUtil.getBundle(APIConnector.class).getBundleContext();
			if (context != null) {
				 osgiRegistration = context.registerService(IAPIConnector.class, this, null);
			}
		}
	}
	
	private void unregisterAsOSGiService() {
		if (osgiRegistration != null) {
			osgiRegistration.unregister();
			osgiRegistration = null;
		}
	}
}