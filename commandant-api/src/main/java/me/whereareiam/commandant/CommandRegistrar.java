package me.whereareiam.commandant;

import me.whereareiam.commandant.model.CommandDefinition;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Interface for programmatically registering commands with Cloud CommandManager.
 * Provides a simple API for registering commands based on CommandDefinition.
 *
 * @param <S> The sender type (e.g., DummyPlayer, CommandSender, Audience)
 */
@SuppressWarnings("unused")
public interface CommandRegistrar<S> {
	/**
	 * Registers a command based on CommandDefinition.
	 * The command will be registered as a subcommand if:
	 * - A root command is set (via {@link #setRootCommand(String)})
	 * - AND the command's usage field contains "{command}"
	 * Otherwise, it registers as a root command.
	 *
	 * @param definition The command definition
	 * @param handler    The command handler that receives the command context
	 */
	void registerCommand(
			@NotNull CommandDefinition definition,
			@NotNull Consumer<CommandContext<S>> handler
	);

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
