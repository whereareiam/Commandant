package me.whereareiam.commandant;

import me.whereareiam.commandant.model.message.ExceptionMessages;
import me.whereareiam.commandant.model.message.MessageResolver;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.exception.*;
import org.incendo.cloud.exception.parsing.NumberParseException;
import org.incendo.cloud.minecraft.extras.AudienceProvider;
import org.incendo.cloud.minecraft.extras.MinecraftExceptionHandler;
import org.incendo.cloud.parser.standard.BooleanParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for registering exception handlers with Cloud command managers.
 * <p>
 * Provides two registration modes:
 * <ul>
 *   <li>Key-based with MessageResolver for locale-aware translations</li>
 *   <li>Model-based with ExceptionMessages for simple static messages</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Locale-aware with translation system
 * ExceptionHandlerRegistrar.register(
 *     commandManager,
 *     ExceptionMessageKeys.defaults(),
 *     (actor, key) -> translations.getMessage(actor.getLocale(), key),
 *     serializer,
 *     Player::getAudience
 * );
 *
 * // Simple static messages
 * ExceptionHandlerRegistrar.register(
 *     commandManager,
 *     config.getExceptionMessages(),
 *     serializer,
 *     Player::getAudience
 * );
 * }</pre>
 * <p>
 * <b>Placeholders:</b> The placeholder name is "content", but the format (e.g., {content}, $content, %content%)
 * is configured in your SerializerEngine at runtime.
 */
@SuppressWarnings("unused")
public final class ExceptionHandlerRegistrar {
	/**
	 * Registers exception handlers with key-based message resolution (locale-aware).
	 * <p>
	 * The MessageResolver will be called with Actor and key to resolve localized messages.
	 * <p>
	 * Placeholder name: <code>content</code> (format configured in SerializerEngine)
	 *
	 * @param commandManager   The command manager to register handlers with
	 * @param keys             The exception message keys to use
	 * @param resolver         Function to resolve message by actor and key
	 * @param serializer       The serializer engine for formatting messages
	 * @param audienceProvider Provider to convert sender to Audience
	 * @param <S>              The sender type (must extend Actor at runtime)
	 * @throws ClassCastException if S does not extend Actor
	 */
	public static <S> void register(
			@NotNull CommandManager<S> commandManager,
			@NotNull ExceptionMessageKeys keys,
			@NotNull MessageResolver resolver,
			@NotNull SerializerEngine serializer,
			@NotNull AudienceProvider<S> audienceProvider
	) {
		MinecraftExceptionHandler.create(audienceProvider)
				.handler(ArgumentParseException.class, (formatter, exception) -> {
					Actor sender = (Actor) exception.context().sender();
					Throwable cause = exception.exception().getCause();

					if (cause instanceof BooleanParser.BooleanParseException e) {
						String message = resolver.resolve(sender, keys.getInvalidSyntaxBoolean());
						return serializer.serialize(sender, message, builder ->
								builder.placeholder("content", e.input()));
					}

					if (cause instanceof NumberParseException e) {
						String message = resolver.resolve(sender, keys.getInvalidSyntaxNumber());
						return serializer.serialize(sender, message, builder ->
								builder.placeholder("content", e.input()));
					}

					if (cause instanceof StringParser.StringParseException e) {
						String message = resolver.resolve(sender, keys.getInvalidSyntaxString());
						return serializer.serialize(sender, message, builder ->
								builder.placeholder("content", e.input()));
					}

					return Component.text("Unknown parse exception occurred: " + cause.getMessage());
				})
				.handler(InvalidCommandSenderException.class, (formatter, exception) -> {
					Actor sender = (Actor) exception.context().sender();
					String message = resolver.resolve(sender, keys.getInvalidSender());
					return serializer.serialize(sender, message, builder ->
							builder.placeholder("content", ""));
				})
				.handler(NoPermissionException.class, (formatter, exception) -> {
					Actor sender = (Actor) exception.context().sender();
					String permission = exception.exception().missingPermission().permissionString();
					String message = resolver.resolve(sender, keys.getNoPermission());
					return serializer.serialize(sender, message, builder ->
							builder.placeholder("content", permission));
				})
				.handler(InvalidSyntaxException.class, (formatter, exception) -> {
					Actor sender = (Actor) exception.context().sender();
					String syntax = exception.exception().correctSyntax().replace("|", "/");
					String message = resolver.resolve(sender, keys.getInvalidSyntax());
					return serializer.serialize(sender, message, builder ->
							builder.placeholder("content", syntax));
				})
				.handler(CommandExecutionException.class, (formatter, exception) -> {
					Actor sender = (Actor) exception.context().sender();
					String errorMessage = exception.exception().getMessage();
					String message = resolver.resolve(sender, keys.getExecutionError());
					return serializer.serialize(sender, message, builder ->
							builder.placeholder("content", errorMessage));
				})
				.registerTo(commandManager);
	}

	/**
	 * Registers exception handlers with static message model (non-locale-aware).
	 * <p>
	 * Uses ExceptionMessages directly without translation.
	 * <p>
	 * Placeholder name: <code>content</code> (format configured in SerializerEngine)
	 *
	 * @param commandManager   The command manager to register handlers with
	 * @param messages         The exception messages to use
	 * @param serializer       The serializer engine for formatting messages
	 * @param audienceProvider Provider to convert sender to Audience
	 * @param <S>              The sender type (must extend Actor at runtime)
	 * @throws ClassCastException if S does not extend Actor
	 */
	public static <S> void register(
			@NotNull CommandManager<S> commandManager,
			@NotNull ExceptionMessages messages,
			@NotNull SerializerEngine serializer,
			@NotNull AudienceProvider<S> audienceProvider
	) {
		MinecraftExceptionHandler.create(audienceProvider)
				.handler(ArgumentParseException.class, (formatter, exception) -> {
					Actor sender = (Actor) exception.context().sender();
					Throwable cause = exception.exception().getCause();

					if (cause instanceof BooleanParser.BooleanParseException e)
						return serializer.serialize(sender, messages.getInvalidSyntaxBoolean(), builder ->
								builder.placeholder("content", e.input()));

					if (cause instanceof NumberParseException e)
						return serializer.serialize(sender, messages.getInvalidSyntaxNumber(), builder ->
								builder.placeholder("content", e.input()));

					if (cause instanceof StringParser.StringParseException e)
						return serializer.serialize(sender, messages.getInvalidSyntaxString(), builder ->
								builder.placeholder("content", e.input()));

					return Component.text("Unknown parse exception occurred: " + cause.getMessage());
				})
				.handler(InvalidCommandSenderException.class, (formatter, exception) -> {
					Actor sender = (Actor) exception.context().sender();
					return serializer.serialize(sender, messages.getInvalidSender(), builder ->
							builder.placeholder("content", ""));
				})
				.handler(NoPermissionException.class, (formatter, exception) -> {
					Actor sender = (Actor) exception.context().sender();
					String permission = exception.exception().missingPermission().permissionString();
					return serializer.serialize(sender, messages.getNoPermission(), builder ->
							builder.placeholder("content", permission));
				})
				.handler(InvalidSyntaxException.class, (formatter, exception) -> {
					Actor sender = (Actor) exception.context().sender();
					String syntax = exception.exception().correctSyntax().replace("|", "/");
					return serializer.serialize(sender, messages.getInvalidSyntax(), builder ->
							builder.placeholder("content", syntax));
				})
				.handler(CommandExecutionException.class, (formatter, exception) -> {
					Actor sender = (Actor) exception.context().sender();
					String errorMessage = exception.exception().getMessage();
					return serializer.serialize(sender, messages.getExecutionError(), builder ->
							builder.placeholder("content", errorMessage));
				})
				.registerTo(commandManager);
	}
}
