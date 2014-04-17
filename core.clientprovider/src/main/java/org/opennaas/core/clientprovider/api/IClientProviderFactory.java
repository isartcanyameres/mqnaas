package org.opennaas.core.clientprovider.api;

import org.opennaas.core.api.ICapability;

public interface IClientProviderFactory extends ICapability {

	<T, CC, C extends IClientProvider<T, CC>> C getClientProvider(Class<C> clientProviderClass);

}
