package me.whereareiam.commandant;

import me.whereareiam.commandant.common.DefaultCommandRegistrar;
import me.whereareiam.commandant.model.CommandDefinition;
import org.incendo.cloud.CommandManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Factory class for creating CommandRegistrar instances and registration helpers.
 * This is the entry point for programmatic command registration.
 */
@SuppressWarnings("unused")
public final class Commandant {
	/**
	 * Creates a new CommandRegistrar instance.
	 *
	 * @param commandManager The command manager to register commands with
	 * @param uuidExtractor  Function to extract UUID from sender for cooldown tracking
	 * @param <S>            The sender type
	 * @return A new CommandRegistrar instance
	 */
	@NotNull
	public static <S> CommandRegistrar<S> createRegistrar(
			@NotNull CommandManager<S> commandManager,
			@NotNull Function<S, UUID> uuidExtractor
	) {
		return DefaultCommandRegistrar.create(commandManager, uuidExtractor);
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

