package me.whereareiam.commandant.common.registration;

import me.whereareiam.commandant.model.CommandDefinition;
import me.whereareiam.commandant.registration.CommandRegistrar;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Default implementation that bridges Cloud annotation parsing with {@link CommandDefinitionRegistration}.
 *
 * @param <S> sender type
 */
public final class AnnotationCommandRegistrar<S> implements CommandRegistrar<S> {
	private final CommandDefinitionRegistration<S> registration;
	private final AnnotationParser<S> annotationParser;
	private final Function<String, CommandDefinition> definitionLookup;

	public AnnotationCommandRegistrar(
			@NotNull CommandDefinitionRegistration<S> registration,
			@NotNull Class<S> senderType,
			@NotNull Function<String, CommandDefinition> definitionLookup
	) {
		this.registration = Objects.requireNonNull(registration, "registration");
		this.definitionLookup = Objects.requireNonNull(definitionLookup, "definitionLookup");

		RecordingCommandManager<S> recordingManager = new RecordingCommandManager<>();
		this.annotationParser = new AnnotationParser<>(recordingManager, senderType);
		this.annotationParser.registerBuilderModifier(
				me.whereareiam.commandant.annotation.Definition.class,
				(annotation, builder) -> builder.meta(CommandDefinitionRegistration.DEFINITION_ID_KEY, annotation.value())
		);
	}

	@Override
	public void register(@NotNull Object... containers) {
		List<Command<S>> commands = new ArrayList<>(this.annotationParser.parse(containers));

		for (Command<S> command : commands) {
			Optional<String> definitionId = command.commandMeta().optional(CommandDefinitionRegistration.DEFINITION_ID_KEY);
			if (definitionId.isEmpty()) continue;

			CommandDefinition definition = definitionLookup.apply(definitionId.get());
			if (definition == null) continue;

			Consumer<CommandContext<S>> handler = ctx -> command.commandExecutionHandler().execute(ctx);
			registration.register(definitionId.get(), definition, handler);
		}
	}

	@Override
	public void setRootCommand(@NotNull String root) {
		this.registration.setRootCommand(root);
	}

	@Override
	public @NotNull Optional<CommandDefinition> resolveDefinition(@NotNull Command<S> command) {
		return command.commandMeta().optional(CommandDefinitionRegistration.DEFINITION_ID_KEY).map(definitionLookup);
	}

	private static final class RecordingCommandManager<S> extends CommandManager<S> {
		RecordingCommandManager() {
			super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
		}

		@Override
		public boolean hasPermission(@NotNull S sender, @NotNull String permission) {
			return true;
		}
	}
}

