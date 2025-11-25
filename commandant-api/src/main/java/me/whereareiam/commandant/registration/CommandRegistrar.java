package me.whereareiam.commandant.registration;

import me.whereareiam.commandant.model.CommandDefinition;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * API surface for registering Cloud-annotated commands that should be mapped to {@link CommandDefinition} entries.
 *
 * @param <S> sender type
 */
public interface CommandRegistrar<S> {
	/**
	 * Registers the provided command containers.
	 *
	 * @param containers instances containing Cloud annotations
	 */
	void register(@NotNull Object... containers);

	/**
	 * Sets the literal root command (e.g., {@code "intercept"}) that annotated subcommands should attach to.
	 *
	 * @param root literal root command name
	 */
	void setRootCommand(@NotNull String root);

	/**
	 * Resolves the {@link CommandDefinition} that was used when registering the given command.
	 *
	 * @param command registered Cloud command
	 * @return optional definition
	 */
	@NotNull
	Optional<CommandDefinition> resolveDefinition(@NotNull Command<S> command);
}