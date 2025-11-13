package me.whereareiam.commandant.model;

import me.whereareiam.keystone.model.Actor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Represents the console command sender.
 * Extends Actor for console command execution.
 */
@SuppressWarnings("unused")
public interface Console extends Actor {
	/**
	 * Console UUID constant for cooldown tracking.
	 */
	UUID CONSOLE_UUID = new UUID(0, 0);

	/**
	 * Gets the console's unique identifier.
	 *
	 * @return A constant UUID for console
	 */
	@NotNull
	@Override
	default UUID getUniqueId() {
		return CONSOLE_UUID;
	}

	/**
	 * Gets the console's username.
	 *
	 * @return "Console"
	 */
	@NotNull
	@Override
	default String getUsername() {
		return "Console";
	}

	/**
	 * Console always has all permissions.
	 *
	 * @param permission The permission to check (ignored)
	 * @return true
	 */
	@Override
	default boolean hasPermission(@NotNull String permission) {
		return true;
	}
}