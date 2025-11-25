package me.whereareiam.commandant.common.registration;

import io.leangen.geantyref.TypeToken;
import me.whereareiam.commandant.model.CommandDefinition;
import me.whereareiam.commandant.registration.CommandRegistrar;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
		List<Command<S>> parsedCommands = new ArrayList<>(this.annotationParser.parse(containers));
		CommandManager<S> commandManager = registration.getCommandManager();
		String rootCommand = registration.getRootCommand();

		for (Command<S> parsedCommand : parsedCommands) {
			Optional<String> definitionId = extractDefinitionId(parsedCommand);
			if (definitionId.isEmpty()) continue;

			CommandDefinition definition = definitionLookup.apply(definitionId.get());
			if (!shouldRegister(definition)) continue;

			Command.Builder<S> builder = buildCommand(
					commandManager,
					rootCommand,
					parsedCommand,
					definition
			);

			builder = applyMetadata(builder, definitionId.get(), definition);
			builder = builder.handler(ctx -> parsedCommand.commandExecutionHandler().executeFuture(ctx));

			commandManager.command(builder);
		}
	}

	private Optional<String> extractDefinitionId(Command<S> command) {
		return command.commandMeta().optional(CommandDefinitionRegistration.DEFINITION_ID_KEY);
	}

	private boolean shouldRegister(CommandDefinition definition) {
		return definition != null && definition.isEnabled();
	}

	private boolean isSubcommand(String rootCommand, CommandDefinition definition) {
		return rootCommand != null &&
				definition.getUsage() != null &&
				definition.getUsage().contains("{command}");
	}

	private Command.Builder<S> buildCommand(
			CommandManager<S> commandManager,
			String rootCommand,
			Command<S> parsedCommand,
			CommandDefinition definition
	) {
		List<CommandComponent<S>> components = parsedCommand.components();
		if (components.isEmpty()) throw new IllegalStateException("Command has no components");

		Command.Builder<S> builder;
		if (isSubcommand(rootCommand, definition)) {
			builder = buildSubcommand(commandManager, rootCommand, components);
		} else {
			builder = buildStandaloneCommand(commandManager, components);
		}

		return builder;
	}

	private Command.Builder<S> buildSubcommand(
			CommandManager<S> commandManager,
			String rootCommand,
			List<CommandComponent<S>> components
	) {
		Command.Builder<S> builder = commandManager.commandBuilder(rootCommand);
		for (CommandComponent<S> component : components)
			builder = addComponent(builder, component);

		return builder;
	}

	private Command.Builder<S> buildStandaloneCommand(
			CommandManager<S> commandManager,
			List<CommandComponent<S>> components
	) {
		CommandComponent<S> firstComponent = components.getFirst();
		Command.Builder<S> builder = commandManager.commandBuilder(firstComponent.name());

		for (int i = 1; i < components.size(); i++)
			builder = addComponent(builder, components.get(i));

		return builder;
	}

	private Command.Builder<S> applyMetadata(
			Command.Builder<S> builder,
			String definitionId,
			CommandDefinition definition
	) {
		builder = builder.meta(CommandDefinitionRegistration.DEFINITION_ID_KEY, definitionId);

		String description = definition.getDescription();
		if (description != null && !description.isBlank())
			builder = builder.commandDescription(Description.of(description));

		String permission = definition.getPermission();
		if (permission != null && !permission.isBlank())
			builder = builder.permission(Permission.of(permission));

		return builder;
	}

	@Override
	public void setRootCommand(@NotNull String root) {
		this.registration.setRootCommand(root);
	}

	@Override
	public @NotNull Optional<CommandDefinition> resolveDefinition(@NotNull Command<S> command) {
		return command.commandMeta().optional(CommandDefinitionRegistration.DEFINITION_ID_KEY).map(definitionLookup);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Command.Builder<S> addComponent(Command.Builder<S> builder, CommandComponent<S> component) {
		CommandComponent.Builder componentBuilder = createComponentBuilder(component);

		if (component.required())
			return builder.required(componentBuilder);

		return builder.optional(componentBuilder);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private CommandComponent.Builder createComponentBuilder(CommandComponent<S> component) {
		ArgumentParser rawParser = component.parser();
		TypeToken rawValueType = component.valueType();
		ParserDescriptor parserDescriptor = ParserDescriptor.of(rawParser, rawValueType);

		CommandComponent.Builder componentBuilder = CommandComponent.builder()
				.name(component.name())
				.parser(parserDescriptor)
				.description(component.description());

		if (component.hasDefaultValue())
			componentBuilder = componentBuilder.defaultValue(component.defaultValue());

		return componentBuilder;
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

