package me.whereareiam.commandant.exception;

import me.whereareiam.commandant.exception.format.CloudPermissionFormatters;
import me.whereareiam.commandant.exception.format.ExceptionFormatting;
import me.whereareiam.commandant.model.message.ExceptionMessages;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.minecraft.extras.AudienceProvider;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExceptionHandlerRegistrarTest {
	private TestCommandManager commandManager;
	private TestActor actor;
	private RecordingSerializerEngine serializer;
	private ExceptionMessages messages;

	@BeforeEach
	void setUp() {
		this.commandManager = new TestCommandManager();
		this.actor = new TestActor();
		this.serializer = new RecordingSerializerEngine();
		this.messages = new ExceptionMessages();
		this.messages.setNoPermission("Missing {content}");
		this.messages.setExecutionError("Error {content}");
		this.messages.setInvalidSyntax("Syntax {content}");
		this.messages.setInvalidSyntaxBoolean("Boolean {content}");
		this.messages.setInvalidSyntaxNumber("Number {content}");
		this.messages.setInvalidSyntaxString("String {content}");
		this.messages.setInvalidSender("Sender");
	}

	@Test
	void legacyRegisterUsesRawPermissionDisplayFormatter() throws Throwable {
		ExceptionHandlerRegistrar.register(
				commandManager,
				messages,
				serializer,
				AudienceProvider.nativeAudience()
		);

		Permission permission = Permission.permission("identica.admin");

		handleNoPermission(permission);

		assertEquals("Missing " + permission.permissionString(), serializer.lastMessage());
	}

	@Test
	void formatterAwareRegisterUsesProvidedPermissionFormatter() throws Throwable {
		ExceptionHandlerRegistrar.register(
				commandManager,
				messages,
				serializer,
				AudienceProvider.nativeAudience(),
				ExceptionFormatting.builder()
						.format(Permission.class, CloudPermissionFormatters.minimal())
						.build()
		);

		Permission permission = Permission.anyOf(
				Permission.permission("identica.admin"),
				Permission.allOf(
						Permission.permission("identica.staff"),
						Permission.permission("identica.audit")
				)
		);

		handleNoPermission(permission);

		assertEquals(
				"Missing identica.admin | (identica.audit & identica.staff)",
				serializer.lastMessage()
		);
	}

	@Test
	void keyBasedFormatterAwareRegisterUsesProvidedPermissionFormatter() throws Throwable {
		ExceptionHandlerRegistrar.register(
				commandManager,
				ExceptionMessageKeys.defaults(),
				(sender, key) -> switch (key) {
					case "commandant.exception.no_permission" -> "Denied {content}";
					case "commandant.exception.execution_error" -> "Error {content}";
					case "commandant.exception.invalid_syntax" -> "Syntax {content}";
					case "commandant.exception.invalid_syntax.boolean" -> "Boolean {content}";
					case "commandant.exception.invalid_syntax.number" -> "Number {content}";
					case "commandant.exception.invalid_syntax.string" -> "String {content}";
					case "commandant.exception.invalid_sender" -> "Sender";
					default -> key;
				},
				serializer,
				AudienceProvider.nativeAudience(),
				ExceptionFormatting.builder()
						.format(Permission.class, permission -> "custom:" + permission.permissionString())
						.build()
		);

		handleNoPermission(Permission.permission("identica.admin"));

		assertEquals("Denied custom:identica.admin", serializer.lastMessage());
	}

	private void handleNoPermission(@NotNull Permission permission) throws Throwable {
		CommandContext<TestActor> context = new CommandContext<>(actor, commandManager);
		List<CommandComponent<?>> currentChain = new ArrayList<>();
		NoPermissionException exception = new NoPermissionException(
				commandManager.testPermission(actor, permission),
				actor,
				currentChain
		);
		commandManager.exceptionController().handleException(context, exception);
	}

	private static final class TestCommandManager extends CommandManager<TestActor> {
		private TestCommandManager() {
			super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
		}

		@Override
		public boolean hasPermission(@NotNull TestActor sender, @NotNull String permission) {
			return sender.hasPermission(permission);
		}
	}

	private static final class TestActor implements Actor, Audience {
		@Override
		public @NotNull UUID getUniqueId() {
			return UUID.randomUUID();
		}

		@Override
		public @NotNull String getUsername() {
			return "tester";
		}

		@Override
		public void sendMessage(@NotNull Component component) {
		}

		@Override
		public boolean hasPermission(@NotNull String permission) {
			return false;
		}

		@Override
		public @NotNull Locale getLocale() {
			return Locale.ENGLISH;
		}

		@Override
		public @NotNull Audience getAudience() {
			return this;
		}
	}

	private static final class RecordingSerializerEngine implements SerializerEngine {
		private String lastMessage = "";

		@Override
		public @NotNull String serialize(@NotNull Component component) {
			return component.toString();
		}

		@Override
		public @NotNull Component serialize(@NotNull SerializerContent content) {
			return Component.text(content.getMessage());
		}

		@Override
		public @NotNull Component serialize(
				@NotNull Actor receiver,
				@NotNull String message,
				@NotNull Consumer<SerializerContent.Builder> consumer
		) {
			SerializerContent.Builder builder = SerializerContent.builder()
					.receiver(receiver)
					.message(message);
			consumer.accept(builder);
			SerializerContent content = builder.build();
			String rendered = content.getMessage();
			for (var entry : content.getPlaceholders().entrySet())
				rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());

			lastMessage = rendered;
			return Component.text(rendered);
		}

		private @NotNull String lastMessage() {
			return lastMessage;
		}
	}
}
