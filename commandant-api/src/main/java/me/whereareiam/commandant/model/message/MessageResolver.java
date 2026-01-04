package me.whereareiam.commandant.model.message;

import me.whereareiam.keystone.Actor;
import org.jetbrains.annotations.NotNull;

/**
 * Functional interface for resolving exception messages by key.
 * Used with translation systems to provide locale-aware exception messages.
 * <p>
 * Example usage:
 * <pre>{@code
 * // With translation system
 * MessageResolver resolver = (actor, key) ->
 *     translationSystem.getMessage(actor.getLocale(), key);
 *
 * // Or as method reference
 * MessageResolver resolver = translationSystem::getMessage;
 * }</pre>
 */
@FunctionalInterface
public interface MessageResolver {
	/**
	 * Resolves a message for the given actor and key.
	 * The actor's locale should be used to determine the appropriate translation.
	 *
	 * @param actor The actor requesting the message (provides locale via getLocale())
	 * @param key   The message key to resolve
	 * @return The resolved message template (may contain placeholders like {content})
	 */
	@NotNull
	String resolve(@NotNull Actor actor, @NotNull String key);
}
