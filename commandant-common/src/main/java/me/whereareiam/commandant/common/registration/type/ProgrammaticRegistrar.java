package me.whereareiam.commandant.common.registration.type;

import me.whereareiam.commandant.common.registration.CommandDefinitionRegistration;
import me.whereareiam.commandant.model.CommandDefinition;
import me.whereareiam.commandant.registration.type.ProgrammaticCommandRegistrar;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implementation of {@link ProgrammaticCommandRegistrar} that registers handlers directly from {@link CommandDefinition}s.
 *
 * @param <S> sender type
 */
public final class ProgrammaticRegistrar<S> implements ProgrammaticCommandRegistrar<S> {
	private final CommandDefinitionRegistration<S> definitionRegistration;

	public ProgrammaticRegistrar(@NotNull CommandDefinitionRegistration<S> definitionRegistration) {
		this.definitionRegistration = definitionRegistration;
	}

	public static <S> ProgrammaticCommandRegistrar<S> create(
			@NotNull CommandManager<S> commandManager,
			@NotNull Function<S, UUID> uuidExtractor
	) {
		return create(commandManager, uuidExtractor, name -> null);
	}

	public static <S> ProgrammaticCommandRegistrar<S> create(
			@NotNull CommandManager<S> commandManager,
			@NotNull Function<S, UUID> uuidExtractor,
			@NotNull Function<String, SuggestionProvider<S>> suggestionResolver
	) {
		CommandDefinitionRegistration<S> registration = new CommandDefinitionRegistration<>(
				commandManager,
				uuidExtractor,
				suggestionResolver
		);
		return new ProgrammaticRegistrar<>(registration);
	}

	@Override
	public void registerCommand(
			@NotNull CommandDefinition definition,
			@NotNull Consumer<CommandContext<S>> handler
	) {
		definitionRegistration.register(definition, handler);
	}

	@Override
	public void setRootCommand(@NotNull String rootCommandName) {
		definitionRegistration.setRootCommand(rootCommandName);
	}

	@Override
	@Nullable
	public String getRootCommand() {
		return definitionRegistration.getRootCommand();
	}

	@Override
	@NotNull
	public CommandManager<S> getCommandManager() {
		return definitionRegistration.getCommandManager();
	}
}

