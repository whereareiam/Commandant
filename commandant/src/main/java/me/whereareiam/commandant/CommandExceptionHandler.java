package me.whereareiam.commandant;

import me.whereareiam.commandant.model.ExceptionMessages;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.exception.*;
import org.incendo.cloud.exception.handling.ExceptionContext;
import org.incendo.cloud.exception.parsing.NumberParseException;
import org.incendo.cloud.minecraft.extras.AudienceProvider;
import org.incendo.cloud.minecraft.extras.MinecraftExceptionHandler;
import org.incendo.cloud.minecraft.extras.caption.ComponentCaptionFormatter;
import org.incendo.cloud.parser.standard.BooleanParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

/**
 * Generic exception handler for command exceptions using ExceptionMessages configuration.
 * This handler can be used across different plugins (Socialismus, Intercept, etc.)
 * by providing a CommandMessageFormatter implementation.
 *
 * <p>Usage example:
 * <pre>{@code
 * // Get ExceptionMessages from your config
 * ExceptionMessages exceptionMessages = config.getCommands().getExceptions();
 *
 * // Create a CommandMessageFormatter that works with your serialization system
 * CommandMessageFormatter<DummyPlayer> formatter = (sender, message, content) ->
 *     Serializer.serialize(new SerializerContent(sender, List.of(
 *         new SerializerPlaceholder("{content}", content)
 *     ), message));
 *
 * // Create the handler
 * CommandExceptionHandler<DummyPlayer> handler = new CommandExceptionHandler<>(
 *     exceptionMessages, formatter
 * );
 *
 * // Register with MinecraftExceptionHandler
 * MinecraftExceptionHandler.create(DummyPlayer::getAudience)
 *     .handler(ArgumentParseException.class, handler::handleParseException)
 *     .handler(InvalidCommandSenderException.class, handler::handleInvalidCommandSenderException)
 *     .handler(NoPermissionException.class, handler::handleNoPermissionException)
 *     .handler(InvalidSyntaxException.class, handler::handleInvalidSyntaxException)
 *     .handler(CommandExecutionException.class, handler::handleCommandExecutionException)
 *     .registerTo(commandManager);
 * }</pre>
 *
 * @param <S> The sender type (e.g., DummyPlayer, CommandSender)
 */
@SuppressWarnings("unused")
public class CommandExceptionHandler<S> {
	private final ExceptionMessages exceptionMessages;
	private final CommandMessageFormatter<S> messageFormatter;

	/**
	 * Creates a new CommandExceptionHandler.
	 *
	 * @param exceptionMessages The exception messages configuration
	 * @param messageFormatter  The formatter to use for formatting command exception messages
	 */
	public CommandExceptionHandler(
			@NotNull ExceptionMessages exceptionMessages,
			@NotNull CommandMessageFormatter<S> messageFormatter
	) {
		this.exceptionMessages = exceptionMessages;
		this.messageFormatter = messageFormatter;
	}

	/**
	 * Handles argument parse exceptions.
	 *
	 * @param formatter The component caption formatter
	 * @param exception The exception context
	 * @return A formatted error message component
	 */
	public Component handleParseException(
			@NotNull ComponentCaptionFormatter<S> formatter,
			@NotNull ExceptionContext<S, ArgumentParseException> exception
	) {
		S sender = exception.context().sender();
		Throwable cause = exception.exception().getCause();

		if (cause instanceof BooleanParser.BooleanParseException e)
			return messageFormatter.format(sender, exceptionMessages.getInvalidSyntaxBoolean(), e.input());

		if (cause instanceof NumberParseException e)
			return messageFormatter.format(sender, exceptionMessages.getInvalidSyntaxNumber(), e.input());

		if (cause instanceof StringParser.StringParseException e)
			return messageFormatter.format(sender, exceptionMessages.getInvalidSyntaxString(), e.input());

		// Fallback for unknown parse exceptions
		return Component.text("Unknown parse exception occurred: " + cause.getMessage());
	}

	/**
	 * Handles invalid command sender exceptions.
	 *
	 * @param formatter The component caption formatter
	 * @param exception The exception context
	 * @return A formatted error message component
	 */
	public Component handleInvalidCommandSenderException(
			@NotNull ComponentCaptionFormatter<S> formatter,
			@NotNull ExceptionContext<S, InvalidCommandSenderException> exception
	) {
		return messageFormatter.format(exception.context().sender(), exceptionMessages.getInvalidSender(), "");
	}

	/**
	 * Handles no permission exceptions.
	 *
	 * @param formatter The component caption formatter
	 * @param exception The exception context
	 * @return A formatted error message component
	 */
	public Component handleNoPermissionException(
			@NotNull ComponentCaptionFormatter<S> formatter,
			@NotNull ExceptionContext<S, NoPermissionException> exception
	) {
		String permission = exception.exception().missingPermission().permissionString();
		return messageFormatter.format(exception.context().sender(), exceptionMessages.getNoPermission(), permission);
	}

	/**
	 * Handles invalid syntax exceptions.
	 *
	 * @param formatter The component caption formatter
	 * @param exception The exception context
	 * @return A formatted error message component
	 */
	public Component handleInvalidSyntaxException(
			@NotNull ComponentCaptionFormatter<S> formatter,
			@NotNull ExceptionContext<S, InvalidSyntaxException> exception
	) {
		String syntax = exception.exception().correctSyntax().replace("|", "/");
		return messageFormatter.format(exception.context().sender(), exceptionMessages.getInvalidSyntax(), syntax);
	}

	/**
	 * Handles command execution exceptions.
	 *
	 * @param formatter The component caption formatter
	 * @param exception The exception context
	 * @return A formatted error message component
	 */
	public Component handleCommandExecutionException(
			@NotNull ComponentCaptionFormatter<S> formatter,
			@NotNull ExceptionContext<S, CommandExecutionException> exception
	) {
		exception.exception().printStackTrace();
		String errorMessage = exception.exception().getMessage();
		return messageFormatter.format(exception.context().sender(), exceptionMessages.getExecutionError(), errorMessage);
	}

	/**
	 * Registers this exception handler with the given command manager.
	 * This is a convenience method that registers all exception handlers at once.
	 *
	 * @param commandManager   The command manager to register handlers with
	 * @param audienceProvider Provider to convert the sender to an Audience (can be a method reference like DummyPlayer::getAudience)
	 */
	public void registerTo(
			@NotNull CommandManager<S> commandManager,
			@NotNull AudienceProvider<S> audienceProvider
	) {
		MinecraftExceptionHandler.create(audienceProvider)
				.handler(ArgumentParseException.class, this::handleParseException)
				.handler(InvalidCommandSenderException.class, this::handleInvalidCommandSenderException)
				.handler(NoPermissionException.class, this::handleNoPermissionException)
				.handler(InvalidSyntaxException.class, this::handleInvalidSyntaxException)
				.handler(CommandExecutionException.class, this::handleCommandExecutionException)
				.registerTo(commandManager);
	}

	/**
	 * Creates and registers a CommandExceptionHandler with the given command manager.
	 * This is a convenience static method that handles everything in one call.
	 *
	 * @param exceptionMessages The exception messages configuration
	 * @param messageFormatter  The formatter to use for formatting command exception messages
	 * @param commandManager    The command manager to register handlers with
	 * @param audienceProvider  Provider to convert the sender to an Audience (can be a method reference like DummyPlayer::getAudience)
	 * @param <S>               The sender type (e.g., DummyPlayer, CommandSender, Audience)
	 */
	public static <S> void register(
			@NotNull ExceptionMessages exceptionMessages,
			@NotNull CommandMessageFormatter<S> messageFormatter,
			@NotNull CommandManager<S> commandManager,
			@NotNull AudienceProvider<S> audienceProvider
	) {
		CommandExceptionHandler<S> handler = new CommandExceptionHandler<>(exceptionMessages, messageFormatter);
		handler.registerTo(commandManager, audienceProvider);
	}
}

