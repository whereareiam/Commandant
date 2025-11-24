package me.whereareiam.commandant.common.registration;

import me.whereareiam.commandant.model.CommandDefinition;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.execution.CommandExecutionHandler;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.processors.cooldown.*;
import org.incendo.cloud.processors.cooldown.listener.ScheduledCleanupCreationListener;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared registration pipeline that converts {@link CommandDefinition} objects into Cloud commands.
 *
 * @param <S> sender type
 */
public final class CommandDefinitionRegistration<S> {
	private final CommandManager<S> commandManager;
	private final Function<S, UUID> uuidExtractor;
	private final Function<String, SuggestionProvider<S>> suggestionResolver;
	private String rootCommand;

	public CommandDefinitionRegistration(
			@NotNull CommandManager<S> commandManager,
			@NotNull Function<S, UUID> uuidExtractor,
			@NotNull Function<String, SuggestionProvider<S>> suggestionResolver
	) {
		this.commandManager = commandManager;
		this.uuidExtractor = uuidExtractor;
		this.suggestionResolver = suggestionResolver;

		CooldownManager<S> cooldownManager = createCooldownManager();
		commandManager.registerCommandPostProcessor(cooldownManager.createPostprocessor());
	}

	public void register(
			@NotNull CommandDefinition definition,
			@NotNull Consumer<CommandContext<S>> handler
	) {
		registerWithFactory(definition, components -> handler::accept);
	}

	public void registerWithFactory(
			@NotNull CommandDefinition definition,
			@NotNull HandlerFactory<S> handlerFactory
	) {
		if (!definition.isEnabled()) return;

		List<String> aliases = definition.getAliases();
		if (aliases == null || aliases.isEmpty()) throw new IllegalArgumentException("Command aliases cannot be empty");

		boolean isSubcommand = rootCommand != null
				&& definition.getUsage() != null
				&& definition.getUsage().contains("{command}");

		if (isSubcommand) {
			registerSubcommand(definition, aliases, handlerFactory);
		} else {
			registerRootCommand(definition, aliases, handlerFactory);
		}
	}

	public void setRootCommand(@NotNull String rootCommandName) {
		this.rootCommand = rootCommandName;
	}

	@Nullable
	public String getRootCommand() {
		return rootCommand;
	}

	@NotNull
	public CommandManager<S> getCommandManager() {
		return commandManager;
	}

	private void registerSubcommand(
			@NotNull CommandDefinition definition,
			@NotNull List<String> aliases,
			@NotNull HandlerFactory<S> handlerFactory
	) {
		Map<Integer, List<List<String>>> aliasesByLength = aliases.stream()
				.map(alias -> List.of(alias.split("\\s+")))
				.collect(Collectors.groupingBy(List::size));

		for (Map.Entry<Integer, List<List<String>>> entry : aliasesByLength.entrySet()) {
			List<List<String>> aliasParts = entry.getValue();
			int numParts = entry.getKey();

			Command.Builder<S> builder = commandManager.commandBuilder(rootCommand);

			for (int i = 0; i < numParts; i++) {
				final int index = i;
				List<String> alternatives = aliasParts.stream()
						.map(parts -> parts.get(index))
						.distinct()
						.toList();

				if (!alternatives.isEmpty()) {
					String mainLiteral = alternatives.get(0);
					String[] remainingLiterals = alternatives.size() > 1
							? alternatives.subList(1, alternatives.size()).toArray(new String[0])
							: new String[0];
					builder = builder.literal(mainLiteral, remainingLiterals);
				}
			}

			builder = builder.commandDescription(Description.of(definition.getDescription() != null
					? definition.getDescription()
					: ""
			));

			Map<String, CommandComponent<S>> componentCollector = new LinkedHashMap<>();
			CommandExecutionHandler<S> commandHandler = handlerFactory.create(componentCollector);

			builder = applyCommandArguments(builder, definition, componentCollector);
			builder = applyCommandProperties(builder, definition);
			registerBuiltCommand(builder, commandHandler);
		}
	}

	private void registerRootCommand(
			@NotNull CommandDefinition definition,
			@NotNull List<String> aliases,
			@NotNull HandlerFactory<S> handlerFactory
	) {
		String mainAlias = aliases.get(0);
		String[] remainingAliases = aliases.size() > 1
				? aliases.subList(1, aliases.size()).toArray(new String[0])
				: new String[0];

		Command.Builder<S> builder = commandManager.commandBuilder(mainAlias, remainingAliases)
				.commandDescription(Description.of(definition.getDescription() != null ? definition.getDescription() : ""));

		Map<String, CommandComponent<S>> componentCollector = new LinkedHashMap<>();
		CommandExecutionHandler<S> commandHandler = handlerFactory.create(componentCollector);

		builder = applyCommandArguments(builder, definition, componentCollector);
		builder = applyCommandProperties(builder, definition);
		registerBuiltCommand(builder, commandHandler);
	}

	private Command.Builder<S> applyCommandArguments(
			@NotNull Command.Builder<S> builder,
			@NotNull CommandDefinition definition,
			@NotNull Map<String, CommandComponent<S>> componentCollector
	) {
		String usage = definition.getUsage();
		if (usage == null || usage.isBlank()) return builder;

		List<UsageArgument> usageArguments = parseUsageArguments(usage);
		if (usageArguments.isEmpty()) return builder;

		Map<String, String> descriptions = definition.getArguments();
		if (!(descriptions instanceof HashMap)) {
			descriptions = new HashMap<>(descriptions);
			definition.setArguments(descriptions);
		}

		for (UsageArgument usageArgument : usageArguments) {
			descriptions.putIfAbsent(usageArgument.name(), usageArgument.name());

			CommandComponent.Builder<S, ?> componentBuilder = usageArgument.greedy()
					? CommandComponent.builder(usageArgument.name(), StringParser.greedyStringParser())
					: CommandComponent.builder(usageArgument.name(), StringParser.stringParser());

			String description = descriptions.get(usageArgument.name());
			if (description != null && !description.isBlank())
				componentBuilder.description(Description.of(description));

			SuggestionProvider<S> suggestions = suggestionResolver.apply(usageArgument.name());
			if (suggestions != null)
				componentBuilder.suggestionProvider(suggestions);

			if (usageArgument.required())
				componentBuilder.required();
			else
				componentBuilder.optional();

			CommandComponent<S> component = componentBuilder.build();
			componentCollector.put(usageArgument.name(), component);
			builder = builder.argument(component);
		}

		return builder;
	}

	private List<UsageArgument> parseUsageArguments(@NotNull String usage) {
		String[] tokens = usage.split("\\s+");
		List<UsageArgument> arguments = new ArrayList<>();

		for (String token : tokens) {
			if (token.isBlank()) continue;

			boolean required = token.startsWith("<") && token.endsWith(">");
			boolean optional = token.startsWith("[") && token.endsWith("]");
			if (!required && !optional) continue;

			String name = token.substring(1, token.length() - 1);
			boolean greedy = name.endsWith("...");
			if (greedy) name = name.substring(0, name.length() - 3);
			if (name.isEmpty()) continue;

			arguments.add(new UsageArgument(name, required, greedy));
		}

		return arguments;
	}

	private Command.Builder<S> applyCommandProperties(
			@NotNull Command.Builder<S> builder,
			@NotNull CommandDefinition definition
	) {
		if (definition.getPermission() != null && !definition.getPermission().isEmpty())
			builder = builder.permission(definition.getPermission());

		if (definition.getCooldown() != null && definition.getCooldown().isEnabled()) {
			Cooldown<S> cooldown = Cooldown.of(
					DurationFunction.constant(Duration.ofSeconds(definition.getCooldown().getDuration())),
					CooldownGroup.named(definition.getCooldown().getGroup())
			);
			builder = builder.apply(cooldown);
		}

		return builder;
	}

	private void registerBuiltCommand(
			@NotNull Command.Builder<S> builder,
			@NotNull CommandExecutionHandler<S> handler
	) {
		Command<S> command = builder.handler(handler).build();
		commandManager.command(command);
	}

	private CooldownManager<S> createCooldownManager() {
		CooldownRepository<S> repository = CooldownRepository.mapping(
				uuidExtractor,
				CooldownRepository.forMap(new HashMap<>())
		);

		ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
		CooldownConfiguration<S> configuration = CooldownConfiguration.<S>builder()
				.repository(repository)
				.addCreationListener(new ScheduledCleanupCreationListener<>(executorService, repository))
				.build();

		return CooldownManager.cooldownManager(configuration);
	}

	private record UsageArgument(String name, boolean required, boolean greedy) {}

	@FunctionalInterface
	public interface HandlerFactory<S> {
		CommandExecutionHandler<S> create(@NotNull Map<String, CommandComponent<S>> components);
	}
}

