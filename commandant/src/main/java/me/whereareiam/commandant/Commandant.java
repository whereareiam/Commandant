package me.whereareiam.commandant;

import me.whereareiam.commandant.common.CommandExceptionHandler;
import me.whereareiam.commandant.common.DefaultCommandRegistrar;
import me.whereareiam.commandant.model.CommandDefinition;
import me.whereareiam.commandant.model.message.ExceptionMessages;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.serializer.SerializerEngine;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.minecraft.extras.AudienceProvider;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Factory class for creating CommandRegistrar instances and registration helpers.
 * This is the entry point for programmatic command registration.
 */
@SuppressWarnings("unused")
public final class Commandant {
	/**
	 * Creates a new CommandRegistrar instance for Actor types.
	 * Uses Actor.getUniqueId() automatically for cooldown tracking.
	 *
	 * @param commandManager The command manager to register commands with
	 * @param <S>            The sender type (must extend Actor)
	 * @return A new CommandRegistrar instance
	 */
	@NotNull
	public static <S extends Actor> CommandRegistrar<S> createRegistrar(
			@NotNull CommandManager<S> commandManager
	) {
		return createRegistrar(commandManager, name -> null);
	}

	@NotNull
	public static <S extends Actor> CommandRegistrar<S> createRegistrar(
			@NotNull CommandManager<S> commandManager,
			@NotNull Function<String, SuggestionProvider<S>> suggestionResolver
	) {
		return DefaultCommandRegistrar.create(commandManager, Actor::getUniqueId, suggestionResolver);
	}

	/**
	 * Creates a command key extractor that matches Command instances to config keys
	 * by comparing their CommandDefinition properties.
	 *
	 * @param commandDefinitions Map of command keys to their CommandDefinition configs
	 * @param <S>                The sender type
	 * @return A function that extracts the command key from a Command instance
	 */
	@NotNull
	public static <S> Function<Command<S>, String> createDefinitionMatcher(
			@NotNull Map<String, CommandDefinition> commandDefinitions
	) {
		return command -> {
			CommandDefinition commandDef = command.getDefinition();
			return commandDefinitions.entrySet().stream()
					.filter(entry -> definitionsMatch(entry.getValue(), commandDef))
					.map(Map.Entry::getKey)
					.findFirst()
					.orElse(null);
		};
	}

	/**
	 * Creates and registers a CommandExceptionHandler with the given command manager.
	 *
	 * @param exceptionMessages The exception message configuration
	 * @param serializer        The serializer engine to use for formatting command exception messages
	 * @param commandManager    The command manager to register handlers with
	 * @param audienceProvider  Provider to convert the sender to an Audience
	 * @param <S>               The sender type (must extend Actor)
	 */
	public static <S extends Actor> void registerExceptionHandler(
			@NotNull ExceptionMessages exceptionMessages,
			@NotNull SerializerEngine serializer,
			@NotNull CommandManager<S> commandManager,
			@NotNull AudienceProvider<S> audienceProvider
	) {
		CommandExceptionHandler.register(exceptionMessages, serializer, commandManager, audienceProvider);
	}

	/**
	 * Compares two CommandDefinition instances to check if they match.
	 *
	 * @param def1 First definition
	 * @param def2 Second definition
	 * @return true if the definitions match
	 */
	private static boolean definitionsMatch(
			@Nullable CommandDefinition def1,
			@Nullable CommandDefinition def2
	) {
		if (def1 == def2) return true;
		if (def1 == null || def2 == null) return false;

		// Compare key properties
		return Objects.equals(def1.getAliases(), def2.getAliases())
				&& def1.isEnabled() == def2.isEnabled()
				&& Objects.equals(def1.getPermission(), def2.getPermission())
				&& Objects.equals(def1.getDescription(), def2.getDescription());
	}
}