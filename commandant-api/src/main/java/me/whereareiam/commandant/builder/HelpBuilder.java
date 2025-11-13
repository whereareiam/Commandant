package me.whereareiam.commandant.builder;

import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Builder interface for creating help command outputs.
 * Formats a list of commands with their arguments and descriptions,
 * optionally including pagination.
 * <p>
 * This builder is generic and works with any sender type.
 *
 * @param <S> The sender type (e.g., Actor, DummyPlayer, CommandSender)
 */
@SuppressWarnings("unused")
public interface HelpBuilder<S> {
	/**
	 * Builds a help message for the given commands.
	 *
	 * @param commands Collection of commands to display
	 * @param page     Current page number (1-indexed)
	 * @return Formatted help message string
	 */
	@NotNull
	String build(@NotNull Collection<Command<S>> commands, int page);
}

