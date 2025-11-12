package me.whereareiam.commandant;

import me.whereareiam.commandant.model.CommandDefinition;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Interface for command implementations.
 * Each command provides its CommandDefinition and handler function.
 * The CommandDefinition can be provided through various means (config, hardcoded, etc.).
 *
 * @param <S> The sender type (e.g., DummyPlayer, CommandSender, Audience)
 */
@SuppressWarnings("unused")
public interface Command<S> {
	/**
	 * Returns the command definition for this command.
	 * The definition can be provided through various means:
	 * - From configuration files
	 * - Hardcoded in the implementation
	 * - Provided by a dependency injection framework
	 *
	 * @return The command definition
	 */
	@NotNull
	CommandDefinition getDefinition();

	/**
	 * Returns the handler function that will be called when the command is executed.
	 *
	 * @return The command handler, never null
	 */
	Consumer<CommandContext<S>> getHandler();
}

