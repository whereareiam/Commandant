package me.whereareiam.commandant.common.registration.type;

import me.whereareiam.commandant.common.registration.CommandDefinitionRegistration;
import me.whereareiam.commandant.model.CommandDefinition;
import me.whereareiam.commandant.registration.type.AnnotationCommandRegistrar;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.annotations.MethodCommandExecutionHandler;
import org.incendo.cloud.annotations.descriptor.ArgumentDescriptor;
import org.incendo.cloud.annotations.descriptor.CommandDescriptor;
import org.incendo.cloud.annotations.descriptor.FlagDescriptor;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.execution.CommandExecutionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Command registrar that adds support for Cloud's annotation API.
 *
 * @param <S> sender type
 */
public final class AnnotationRegistrar<S> implements AnnotationCommandRegistrar<S> {
	private final CommandDefinitionRegistration<S> definitionRegistration;

	public AnnotationRegistrar(
			@NotNull CommandDefinitionRegistration<S> definitionRegistration
	) {
		this.definitionRegistration = definitionRegistration;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void registerAnnotatedCommand(
			@NotNull CommandDefinition definition,
			@NotNull Object annotatedCommand,
			@NotNull Class<? extends S> senderType
	) {
		if (!definition.isEnabled()) return;

		CommandManager<S> commandManager = definitionRegistration.getCommandManager();
		AnnotationParser<S> parser = new AnnotationParser<>(commandManager, (Class<S>) senderType);
		processAnnotationInstance(parser, annotatedCommand);

		Collection<CommandDescriptor> descriptors = parser.commandExtractor().extractCommands(annotatedCommand);
		if (descriptors.isEmpty())
			throw new IllegalArgumentException("No @Command methods found on " + annotatedCommand.getClass().getName());

		CommandDescriptor descriptor = selectDescriptorForDefinition(definition, descriptors);
		if (descriptor == null) {
			throw new IllegalArgumentException("No @Command method matched aliases " + definition.getAliases()
					+ " in " + annotatedCommand.getClass().getName());
		}

		definitionRegistration.registerWithFactory(
				definition,
				componentCollector -> createAnnotationExecutionHandler(parser, annotatedCommand, descriptor, componentCollector)
		);
	}


	@Override
	public void setRootCommand(@NotNull String rootCommandName) {
		definitionRegistration.setRootCommand(rootCommandName);
	}

	@Override
	public @Nullable String getRootCommand() {
		return definitionRegistration.getRootCommand();
	}

	@Override
	public @NotNull CommandManager<S> getCommandManager() {
		return definitionRegistration.getCommandManager();
	}

	private CommandExecutionHandler<S> createAnnotationExecutionHandler(
			@NotNull AnnotationParser<S> parser,
			@NotNull Object annotatedCommand,
			@NotNull CommandDescriptor descriptor,
			@NotNull Map<String, CommandComponent<S>> componentCollector
	) {
		Method method = descriptor.method();
		Collection<ArgumentDescriptor> argumentDescriptors = parser.argumentExtractor().extractArguments(descriptor.syntax(), method);
		Collection<FlagDescriptor> flagDescriptors = parser.flagExtractor().extractFlags(method);

		MethodCommandExecutionHandler.CommandMethodContext<S> context = createCommandMethodContext(
				annotatedCommand,
				componentCollector,
				argumentDescriptors,
				flagDescriptors,
				method,
				parser
		);

		return new MethodCommandExecutionHandler<>(context);
	}

	@SuppressWarnings("unchecked")
	private MethodCommandExecutionHandler.CommandMethodContext<S> createCommandMethodContext(
			@NotNull Object annotatedCommand,
			@NotNull Map<String, CommandComponent<S>> componentCollector,
			@NotNull Collection<ArgumentDescriptor> argumentDescriptors,
			@NotNull Collection<FlagDescriptor> flagDescriptors,
			@NotNull Method method,
			@NotNull AnnotationParser<S> parser
	) {
		try {
			Class<?> contextClass = MethodCommandExecutionHandler.CommandMethodContext.class;
			Constructor<MethodCommandExecutionHandler.CommandMethodContext<S>> constructor =
					(Constructor<MethodCommandExecutionHandler.CommandMethodContext<S>>) contextClass
							.getDeclaredConstructor(
									Object.class,
									Map.class,
									Collection.class,
									Collection.class,
									Method.class,
									AnnotationParser.class
							);

			constructor.setAccessible(true);
			return constructor.newInstance(
					annotatedCommand,
					componentCollector,
					argumentDescriptors,
					flagDescriptors,
					method,
					parser
			);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unable to construct MethodCommandExecutionHandler context", exception);
		}
	}

	private void processAnnotationInstance(@NotNull AnnotationParser<S> parser, @NotNull Object instance) {
		invokeAnnotationParser(parser, "parseDefaultValues", instance);
		invokeAnnotationParser(parser, "parseSuggestions", instance);
		invokeAnnotationParser(parser, "parseParsers", instance);
		invokeAnnotationParser(parser, "parseExceptionHandlers", instance);
	}

	private void invokeAnnotationParser(@NotNull AnnotationParser<S> parser, @NotNull String methodName, @NotNull Object instance) {
		try {
			Method reflectionMethod = AnnotationParser.class.getDeclaredMethod(methodName, Object.class);
			reflectionMethod.setAccessible(true);
			reflectionMethod.invoke(parser, instance);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Failed to invoke AnnotationParser#" + methodName, exception);
		}
	}

	private CommandDescriptor selectDescriptorForDefinition(
			@NotNull CommandDefinition definition,
			@NotNull Collection<CommandDescriptor> descriptors
	) {
		List<String> aliases = definition.getAliases();
		if (aliases == null || aliases.isEmpty())
			throw new IllegalArgumentException("Command aliases cannot be empty");

		Set<String> normalizedAliases = aliases.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(alias -> !alias.isEmpty())
				.map(this::firstAliasToken)
				.map(String::toLowerCase)
				.collect(Collectors.toSet());

		for (CommandDescriptor descriptor : descriptors) {
			String token = descriptor.commandToken();
			if (normalizedAliases.contains(token.toLowerCase()))
				return descriptor;
		}

		return null;
	}

	private String firstAliasToken(@NotNull String alias) {
		int spaceIndex = alias.indexOf(' ');
		return spaceIndex == -1 ? alias : alias.substring(0, spaceIndex);
	}
}