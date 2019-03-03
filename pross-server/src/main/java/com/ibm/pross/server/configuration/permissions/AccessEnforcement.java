package com.ibm.pross.server.configuration.permissions;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.ibm.pross.server.configuration.permissions.ClientPermissions.Permissions;
import com.ibm.pross.server.configuration.permissions.exceptions.NotFoundException;
import com.ibm.pross.server.configuration.permissions.exceptions.UnauthorizedException;

public class AccessEnforcement {

	private final ConcurrentMap<Integer, ClientPermissions> permissionMap;
	private final Set<String> knownSecrets;

	public AccessEnforcement(final Map<Integer, ClientPermissions> permissionMap, Set<String> knownSecrets) {
		this.permissionMap = new ConcurrentHashMap<>(permissionMap);
		this.knownSecrets = Collections.unmodifiableSet(knownSecrets);;
	}

	public void enforceAccess(final Integer clientId, final String secretName, final Permissions permission)
			throws UnauthorizedException, NotFoundException {

		if (!knownSecrets.contains(secretName))
		{
			throw new NotFoundException();
		}
		
		if (clientId == null)
		{
			// Client is anonymous
			throw new UnauthorizedException();
		}
		
		// Get this client's permissions
		final ClientPermissions clientPermissions = this.permissionMap.get(clientId);

		if (clientPermissions == null) {
			// Client is unknown
			throw new UnauthorizedException();
		} else {
			// Client is known but is not authorized
			if (!clientPermissions.hasPermission(secretName, permission)) {
				throw new UnauthorizedException();
			}
		}
	}
	
	public Set<String> getKnownSecrets()
	{
		// Not modifiable
		return knownSecrets;
	}

	private static final class DummyAccessEnforcement extends AccessEnforcement {

		public DummyAccessEnforcement() {
			super(Collections.emptyMap(), Collections.emptySet());
		}

		@Override
		public void enforceAccess(final Integer clientId, final String secretName, final Permissions permission)
				throws UnauthorizedException {
			// Always allow
		}
	}

	@Deprecated
	public static final AccessEnforcement INSECURE_DUMMY_ENFORCEMENT = new DummyAccessEnforcement();


}