package me.whereareiam.commandant;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Functional interface for formatting command exception messages with placeholders.
 * Used by CommandExceptionHandler to format exception messages for commands.
 *
 * @param <S> The sender type (e.g., DummyPlayer, CommandSender)
 */
@FunctionalInterface
public interface CommandMessageFormatter<S> {
	/**
	 * Formats a command exception message template by replacing placeholders.
	 *
	 * @param sender  The command sender
	 * @param message The message template (may contain {content} placeholder)
	 * @param content The content to replace {content} with
	 * @return A formatted Component
	 */
	@NotNull
	Component format(@NotNull S sender, @NotNull String message, @NotNull String content);
}

