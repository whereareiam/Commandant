package me.whereareiam.commandant.registration.type;

import me.whereareiam.commandant.model.CommandDefinition;
import me.whereareiam.commandant.registration.CommandRegistrar;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for registering commands using Cloud's annotation API.
 * Extends {@link CommandRegistrar} to provide annotation-based command registration
 * while maintaining access to root command management and command manager.
 *
 * @param <S> The sender type (e.g., DummyPlayer, CommandSender, Audience)
 */
@SuppressWarnings("unused")
public interface AnnotationCommandRegistrar<S> extends CommandRegistrar<S> {
	/**
	 * Registers a command whose execution handler is provided through Cloud's annotations API,
	 * while the command structure (aliases, usage, description, permissions, etc.) is still
	 * driven by {@link CommandDefinition}.
	 *
	 * @param definition       Command definition describing the structure
	 * @param annotatedCommand Instance that contains {@link Command} methods
	 * @param senderType       Sender type used by the annotation parser
	 */
	void registerAnnotatedCommand(
			@NotNull CommandDefinition definition,
			@NotNull Object annotatedCommand,
			@NotNull Class<? extends S> senderType
	);
}

