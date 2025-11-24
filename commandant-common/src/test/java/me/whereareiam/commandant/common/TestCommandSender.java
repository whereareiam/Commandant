package me.whereareiam.commandant.common;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Test command sender for unit testing.
 */
public class TestCommandSender {
	private final Set<String> permissions = new HashSet<>();
	private final UUID uuid;

	public TestCommandSender() {
		this.uuid = UUID.randomUUID();
	}

	public TestCommandSender(final String... permissions) {
		this();
		this.permissions.addAll(Arrays.asList(permissions));
	}

	public boolean hasPermission(final String permission) {
		return this.permissions.contains(permission);
	}

	public UUID getUuid() {
		return uuid;
	}

	@Override
	public String toString() {
		return "TestCommandSender{uuid=" + uuid + ", permissions=" + this.permissions + "}";
	}
}
