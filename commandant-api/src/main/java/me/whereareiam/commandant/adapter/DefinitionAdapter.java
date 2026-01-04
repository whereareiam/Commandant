package me.whereareiam.commandant.adapter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface DefinitionAdapter<T> {
	boolean isEnabled(@NotNull T definition);

	@Nullable List<String> getAliases(@NotNull T definition);

	@Nullable String getPermission(@NotNull T definition);

	@Nullable String getDescription(@NotNull T definition);

	@Nullable String getUsage(@NotNull T definition);

	@Nullable Cooldown getCooldown(@NotNull T definition);

	@Nullable Map<String, String> getArguments(@NotNull T definition);

	interface Cooldown {
		boolean isEnabled();

		int getDuration();

		@NotNull String getGroup();
	}
}
