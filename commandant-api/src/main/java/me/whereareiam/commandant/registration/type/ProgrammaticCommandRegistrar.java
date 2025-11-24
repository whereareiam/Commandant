package me.whereareiam.commandant.registration.type;

import me.whereareiam.commandant.model.CommandDefinition;
import me.whereareiam.commandant.registration.CommandRegistrar;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Interface for programmatically registering commands with Cloud CommandManager.
 * Extends {@link CommandRegistrar} to provide programmatic command registration
 * based on {@link CommandDefinition}.
 *
 * @param <S> The sender type (e.g., DummyPlayer, CommandSender, Audience)
 */
@SuppressWarnings("unused")
public interface ProgrammaticCommandRegistrar<S> extends CommandRegistrar<S> {
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
}

