package org.mqnaas.examples.testapp;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import org.mqnaas.core.api.Endpoint;
import org.mqnaas.core.api.IApplication;
import org.mqnaas.core.api.IExecutionService;
import org.mqnaas.core.api.IObservationService;
import org.mqnaas.core.api.IResource;
import org.mqnaas.core.api.IRootResource;
import org.mqnaas.core.api.IRootResourceAdministration;
import org.mqnaas.core.api.IRootResourceProvider;
import org.mqnaas.core.api.IService;
import org.mqnaas.core.api.IServiceProvider;
import org.mqnaas.core.api.Specification;
import org.mqnaas.core.api.Specification.Type;
import org.mqnaas.core.api.annotations.DependingOn;
import org.mqnaas.core.api.exceptions.ResourceNotFoundException;
import org.mqnaas.core.api.exceptions.ServiceNotFoundException;
import org.mqnaas.core.impl.BindingManagement;
import org.mqnaas.core.impl.notificationfilter.ServiceFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyTestApplication implements IApplication {

	private static final Logger			log	= LoggerFactory.getLogger(BindingManagement.class);

	@DependingOn
	private IRootResourceAdministration	resourceAdministration;

	@DependingOn
	IRootResourceProvider				resourceProvider;

	@DependingOn
	private IServiceProvider			serviceProvider;

	@DependingOn
	private IExecutionService			executionService;

	@DependingOn
	private IObservationService			observationService;

	@Override
	public void activate() {

		IRootResource mqNaaS;
		try {
			mqNaaS = resourceProvider.getRootResource(new Specification(Specification.Type.CORE));
		} catch (ResourceNotFoundException e) {
			// this should not happen
			log.error("No CORE resource found!", e);
			return;
		}

		IService observedService = null;
		IService notifiedService = null;
		try {
			observedService = serviceProvider.getService(mqNaaS, "resourceAdded", IResource.class, IApplication.class, Class.class);
			notifiedService = serviceProvider.getService(mqNaaS, "printAvailableServices");
		} catch (ServiceNotFoundException e) {
			log.error("No CORE services found!", e);
			return;
		}

		try {
			executionService.execute(notifiedService, null);

			observationService.registerObservation(new ServiceFilter(observedService), notifiedService);

			resourceAdministration.createRootResource(new Specification(Type.ROUTER, "Junos"), Arrays.asList(new Endpoint()));

			resourceAdministration.createRootResource(new Specification(Type.ROUTER, "Opener"), Arrays.asList(new Endpoint()));

			resourceAdministration.createRootResource(new Specification(Type.ROUTER, "Junos"), Arrays.asList(new Endpoint()));

			resourceAdministration.createRootResource(new Specification(Type.ROUTER, "Opener"), Arrays.asList(new Endpoint()));

			resourceAdministration.createRootResource(new Specification(Type.ROUTER, "Junos"), Arrays.asList(new Endpoint()));

		} catch (InvocationTargetException e) {
			log.error("Could not activate MyTestApplication:", e.getCause());
		}

	}

	@Override
	public void deactivate() {
		// TODO Auto-generated method stub

	}

}