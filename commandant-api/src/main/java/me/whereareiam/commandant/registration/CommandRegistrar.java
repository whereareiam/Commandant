package me.whereareiam.commandant.registration;

import org.incendo.cloud.CommandManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base interface for command registrars.
 * Provides common functionality for managing root commands and accessing the command manager.
 *
 * @param <S> The sender type (e.g., DummyPlayer, CommandSender, Audience)
 */
@SuppressWarnings("unused")
public interface CommandRegistrar<S> {
	/**
	 * Sets the root command. After this is called, commands with "{command}" in their usage
	 * field will be registered as subcommands under this root command.
	 *
	 * @param rootCommandName The root command name (e.g., "intercept")
	 */
	void setRootCommand(@NotNull String rootCommandName);

	/**
	 * Gets the current root command name, if set.
	 *
	 * @return The root command name, or null if not set
	 */
	@Nullable
	String getRootCommand();

	/**
	 * Gets the underlying command manager.
	 *
	 * @return The command manager
	 */
	@NotNull
	CommandManager<S> getCommandManager();
}
