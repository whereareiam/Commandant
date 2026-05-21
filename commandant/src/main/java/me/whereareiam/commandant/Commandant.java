package me.whereareiam.commandant;

import me.whereareiam.commandant.adapter.DefinitionAdapter;
import me.whereareiam.commandant.common.parsing.DefinitionParser;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Helper library for applying definition-based configuration to Cloud commands.
 * <p>
 * Commandant provides utilities for processing already-parsed commands and applying
 * definition-based overrides (aliases, permissions, descriptions, cooldowns, etc.).
 * <p>
 * Typical usage with custom annotation parser:
 * <pre>{@code
 * // 1. Parse commands with your custom annotation parser
 * Collection<Command<Player>> commands = myAnnotationParser.parse(new HelpCommand());
 *
 * // 2. Process each command with definition overrides
 * for (Command<Player> cmd : commands) {
 *     String defId = extractDefinitionId(cmd);
 *     MyDefinition definition = lookupDefinition(defId);
 *
 *     Commandant.process(cmd, commandManager)
 *         .withDefinition(definition, adapter)
 *         .modify(builder -> {
 *             // Add custom metadata
 *             builder.meta(MY_KEY, value);
 *         })
 *         .register();
 * }
 * }</pre>
 */
public final class Commandant {
	/**
	 * Process a single parsed command with optional definition-based overrides.
	 * <p>
	 * This is useful when using custom annotation parsers. Parse your commands first,
	 * then process each one with this method to apply definition overrides.
	 *
	 * @param command        the parsed command
	 * @param commandManager the Cloud command manager
	 * @param <S>            sender type
	 * @return command processor for fluent API
	 */
	@NotNull
	public static <S> CommandProcessor<S> process(
			@NotNull Command<S> command,
			@NotNull CommandManager<S> commandManager
	) {
		return new CommandProcessor<>(command, commandManager);
	}

	/**
	 * Processor for individual commands with optional definition overrides.
	 */
	public static final class CommandProcessor<S> {
		private final Command<S> command;
		private final CommandManager<S> commandManager;

		CommandProcessor(
				@NotNull Command<S> command,
				@NotNull CommandManager<S> commandManager
		) {
			this.command = Objects.requireNonNull(command, "command");
			this.commandManager = Objects.requireNonNull(commandManager, "commandManager");
		}

		/**
		 * Apply definition-based overrides to this command.
		 * <p>
		 * If the definition is null or the command has no definition ID metadata,
		 * the command is registered as-is without modifications.
		 *
		 * @param definition the definition to apply (can be null)
		 * @param adapter    adapter for extracting values from the definition
		 * @param <D>        definition type
		 * @return builder processor for fluent API
		 */
		@NotNull
		public <D> BuilderProcessor<S> withDefinition(
				@Nullable D definition,
				@NotNull DefinitionAdapter<D> adapter
		) {
			return withDefinition(definition, adapter, List.of());
		}

		/**
		 * Apply definition-based overrides to this command with explicit root aliases.
		 * <p>
		 * This is useful when a command family should share one root tree but expose
		 * multiple root labels, such as {@code /identica} and {@code /auth}.
		 *
		 * @param definition  the definition to apply (can be null)
		 * @param adapter     adapter for extracting values from the definition
		 * @param rootAliases root aliases for the shared command tree
		 * @param <D>         definition type
		 * @return builder processor for fluent API
		 */
		@NotNull
		public <D> BuilderProcessor<S> withDefinition(
				@Nullable D definition,
				@NotNull DefinitionAdapter<D> adapter,
				@NotNull List<String> rootAliases
		) {
			Objects.requireNonNull(adapter, "adapter");
			Objects.requireNonNull(rootAliases, "rootAliases");

			String defId = command.commandMeta()
					.optional(CommandantKeys.DEFINITION_ID)
					.orElse(null);

			// No definition or no definition ID - register as-is
			if (definition == null || defId == null) {
				commandManager.command(command);
				return new BuilderProcessor<>(List.of(), commandManager);
			}

			// Apply definition overrides
			DefinitionParser<S, D> parser = new DefinitionParser<>(commandManager, adapter);
			List<Command.Builder<S>> builders = parser.applyOverrides(
					definition, defId, command, rootAliases
			);

			return new BuilderProcessor<>(builders, commandManager);
		}

		/**
		 * Register the command without definition processing.
		 *
		 * @return builder processor (empty, as command was already registered)
		 */
		@NotNull
		public BuilderProcessor<S> withoutDefinition() {
			commandManager.command(command);
			return new BuilderProcessor<>(List.of(), commandManager);
		}
	}

	/**
	 * Processor for command builders, allowing modification before registration.
	 */
	public static final class BuilderProcessor<S> {
		private final List<Command.Builder<S>> builders;
		private final CommandManager<S> commandManager;

		BuilderProcessor(
				@NotNull List<Command.Builder<S>> builders,
				@NotNull CommandManager<S> commandManager
		) {
			this.builders = Objects.requireNonNull(builders, "builders");
			this.commandManager = Objects.requireNonNull(commandManager, "commandManager");
		}

		/**
		 * Modify all builders before registration.
		 * <p>
		 * The consumer is called once for each builder. This is useful for
		 * adding custom metadata or modifying command properties.
		 *
		 * @param modifier consumer that modifies builders
		 * @return this processor for chaining
		 */
		@NotNull
		public BuilderProcessor<S> modify(@NotNull Consumer<Command.Builder<S>> modifier) {
			Objects.requireNonNull(modifier, "modifier");
			builders.forEach(modifier);
			return this;
		}

		/**
		 * Access the command builders for custom processing.
		 *
		 * @return unmodifiable list of builders
		 */
		@NotNull
		public List<Command.Builder<S>> builders() {
			return Collections.unmodifiableList(builders);
		}

		/**
		 * Build and register all commands to the command manager.
		 */
		public void register() {
			builders.forEach(builder -> commandManager.command(builder.build()));
		}

		/**
		 * Build all commands without registering them.
		 *
		 * @return list of built commands
		 */
		@NotNull
		public List<Command<S>> build() {
			return builders.stream()
					.map(Command.Builder::build)
					.toList();
		}
	}
}
