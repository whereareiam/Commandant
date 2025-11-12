package me.whereareiam.commandant.common;

import me.whereareiam.commandant.CommandRegistrar;
import me.whereareiam.commandant.model.CommandDefinition;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.execution.CommandExecutionHandler;
import org.incendo.cloud.processors.cooldown.*;
import org.incendo.cloud.processors.cooldown.listener.ScheduledCleanupCreationListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implementation of CommandRegistrar for programmatically registering commands with Cloud CommandManager.
 *
 * @param <S> The sender type (e.g., DummyPlayer, CommandSender, Audience)
 */
public class DefaultCommandRegistrar<S> implements CommandRegistrar<S> {
	private final CommandManager<S> commandManager;
	private final Function<S, UUID> uuidExtractor;
	private String rootCommand;

	/**
	 * Creates a new CommandRegistrar.
	 *
	 * @param commandManager The command manager to register commands with
	 * @param uuidExtractor  Function to extract UUID from sender for cooldown tracking
	 */
	private DefaultCommandRegistrar(
			@NotNull CommandManager<S> commandManager,
			@NotNull Function<S, UUID> uuidExtractor
	) {
		this.commandManager = commandManager;
		this.uuidExtractor = uuidExtractor;
		CooldownManager<S> cooldownManager = createCooldownManager();
		commandManager.registerCommandPostProcessor(cooldownManager.createPostprocessor());
	}

	/**
	 * Creates a new CommandRegistrar instance.
	 *
	 * @param commandManager The command manager to register commands with
	 * @param uuidExtractor  Function to extract UUID from sender for cooldown tracking
	 * @param <S>            The sender type
	 * @return A new CommandRegistrar instance
	 */
	@NotNull
	public static <S> CommandRegistrar<S> create(
			@NotNull CommandManager<S> commandManager,
			@NotNull Function<S, UUID> uuidExtractor
	) {
		return new DefaultCommandRegistrar<>(commandManager, uuidExtractor);
	}

	@Override
	public void registerCommand(
			@NotNull CommandDefinition definition,
			@NotNull Consumer<CommandContext<S>> handler
	) {
		if (!definition.isEnabled()) return;

		List<String> aliases = definition.getAliases();
		if (aliases == null || aliases.isEmpty()) throw new IllegalArgumentException("Command aliases cannot be empty");

		// Check if usage contains {command} to determine if it should be a subcommand
		boolean isSubcommand = rootCommand != null
				&& definition.getUsage() != null
				&& definition.getUsage().contains("{command}");

		if (isSubcommand) {
			registerSubcommand(definition, aliases, handler);
			return;
		}

		registerRootCommand(definition, aliases, handler);
	}

	private void registerSubcommand(
			@NotNull CommandDefinition definition,
			@NotNull List<String> aliases,
			@NotNull Consumer<CommandContext<S>> handler
	) {
		for (String alias : aliases) {
			Command.Builder<S> builder = commandManager.commandBuilder(rootCommand)
					.literal(alias)
					.commandDescription(Description.of(definition.getDescription() != null
							? definition.getDescription()
							: ""
					));

			applyCommandProperties(builder, definition);
			registerBuiltCommand(builder, handler);
		}
	}

	private void registerRootCommand(
			@NotNull CommandDefinition definition,
			@NotNull List<String> aliases,
			@NotNull Consumer<CommandContext<S>> handler
	) {
		for (String alias : aliases) {
			Command.Builder<S> builder = commandManager.commandBuilder(alias)
					.commandDescription(Description.of(definition.getDescription() != null ? definition.getDescription() : ""));

			applyCommandProperties(builder, definition);
			registerBuiltCommand(builder, handler);
		}
	}

	private void applyCommandProperties(
			@NotNull Command.Builder<S> builder,
			@NotNull CommandDefinition definition
	) {
		// Add permission if specified
		if (definition.getPermission() != null && !definition.getPermission().isEmpty())
			builder.permission(definition.getPermission());

		// Add cooldown if enabled
		if (definition.getCooldown() != null && definition.getCooldown().isEnabled()) {
			Cooldown<S> cooldown = Cooldown.of(
					DurationFunction.constant(Duration.ofSeconds(definition.getCooldown().getDuration())),
					CooldownGroup.named(definition.getCooldown().getGroup())
			);
			builder.apply(cooldown);
		}
	}

	private void registerBuiltCommand(
			@NotNull Command.Builder<S> builder,
			@NotNull Consumer<CommandContext<S>> handler
	) {
		CommandExecutionHandler<S> executionHandler = handler::accept;
		Command<S> command = builder.handler(executionHandler).build();
		commandManager.command(command);
	}

	/**
	 * Creates a cooldown manager for command cooldowns.
	 *
	 * @return The cooldown manager
	 */
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

	@Override
	public void setRootCommand(@NotNull String rootCommandName) {
		this.rootCommand = rootCommandName;
	}

	@Override
	@Nullable
	public String getRootCommand() {
		return rootCommand;
	}

	@Override
	@NotNull
	public CommandManager<S> getCommandManager() {
		return commandManager;
	}
}

