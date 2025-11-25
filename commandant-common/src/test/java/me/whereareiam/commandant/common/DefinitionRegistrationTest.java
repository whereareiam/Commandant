package me.whereareiam.commandant.common;

import me.whereareiam.commandant.common.registration.CommandDefinitionRegistration;
import me.whereareiam.commandant.model.CommandDefinition;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class DefinitionRegistrationTest {
	private CommandManager<TestCommandSender> commandManager;
	private CommandDefinitionRegistration<TestCommandSender> registration;

	@BeforeEach
	void setUp() {
		this.commandManager = new CommandManager<>(
				ExecutionCoordinator.simpleCoordinator(),
				CommandRegistrationHandler.nullCommandRegistrationHandler()
		) {
			@Override
			public boolean hasPermission(
					final TestCommandSender sender,
					final String permission
			) {
				return sender.hasPermission(permission);
			}
		};

		this.registration = new CommandDefinitionRegistration<>(
				commandManager
		);
	}

	@Test
	void testRootCommandRegistration() {
		CommandDefinition definition = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("test", "t"))
				.description("Test command")
				.build();

		Consumer<CommandContext<TestCommandSender>> handler = context -> {};

		registration.register(definition, handler);

		Collection<Command<TestCommandSender>> commands = commandManager.commands();
		assertEquals(1, commands.size());

		Command<TestCommandSender> command = commands.iterator().next();
		assertEquals(1, command.components().size());
		assertEquals("test", command.components().get(0).name());
		assertEquals("Test command", command.commandDescription().description().textDescription());

		assertNotNull(commandManager.commandTree().getNamedNode("test"));
		assertNotNull(commandManager.commandTree().getNamedNode("t"));
	}

	@Test
	void testCommandWithPermission() {
		CommandDefinition definition = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin"))
				.permission("admin.test")
				.build();

		registration.register(definition, context -> {});

		Command<TestCommandSender> command = commandManager.commands().iterator().next();
		assertEquals("admin.test", command.commandPermission().permissionString());

		TestCommandSender senderWithPermission = new TestCommandSender("admin.test");
		TestCommandSender senderWithoutPermission = new TestCommandSender();
		assertTrue(commandManager.testPermission(senderWithPermission, command.commandPermission()).allowed());
		assertFalse(commandManager.testPermission(senderWithoutPermission, command.commandPermission()).allowed());
	}

	@Test
	void testCommandWithArguments() {
		Map<String, String> args = new HashMap<>();
		args.put("player", "Player name");
		args.put("message", "Optional message");
		args.put("text", "Greedy text");

		CommandDefinition definition = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("test"))
				.usage("<player> <text...> [message]")
				.arguments(args)
				.build();

		registration.register(definition, context -> {});

		Command<TestCommandSender> command = commandManager.commands().iterator().next();
		assertEquals(4, command.components().size());

		CommandComponent<TestCommandSender> playerArg = command.components().get(1);
		assertEquals("player", playerArg.name());
		assertTrue(playerArg.required());
		assertEquals("Player name", playerArg.description().textDescription());

		CommandComponent<TestCommandSender> textArg = command.components().get(2);
		assertEquals("text", textArg.name());
		assertTrue(textArg.required());

		CommandComponent<TestCommandSender> messageArg = command.components().get(3);
		assertEquals("message", messageArg.name());
		assertFalse(messageArg.required());
	}

	@Test
	void testSubcommandRegistration() {
		registration.setRootCommand("intercept");

		CommandDefinition definition = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("reload", "rl"))
				.usage("{command} reload")
				.description("Reload subcommand")
				.build();

		registration.register(definition, context -> {});

		Command<TestCommandSender> command = commandManager.commands().iterator().next();
		assertEquals(2, command.components().size());
		assertEquals("intercept", command.components().get(0).name());
		assertTrue(command.components().get(1).name().equals("reload") ||
				command.components().get(1).name().equals("rl"));
	}

	@Test
	void testSubcommandWithMultiWordAlias() {
		registration.setRootCommand("intercept");

		CommandDefinition definition = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("database upload", "upload"))
				.usage("{command} database upload")
				.build();

		registration.register(definition, context -> {});

		Collection<Command<TestCommandSender>> commands = commandManager.commands();
		assertFalse(commands.isEmpty());

		boolean foundDatabaseUpload = commands.stream()
				.anyMatch(cmd -> cmd.components().size() >= 3 &&
						"database".equals(cmd.components().get(1).name()) &&
						"upload".equals(cmd.components().get(2).name()));
		assertTrue(foundDatabaseUpload);
	}

	@Test
	void testSubcommandWithArguments() {
		registration.setRootCommand("intercept");

		Map<String, String> args = new HashMap<>();
		args.put("key", "Key");
		args.put("value", "Value");

		CommandDefinition definition = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("set"))
				.usage("{command} set <key> <value>")
				.arguments(args)
				.build();

		registration.register(definition, context -> {});

		Command<TestCommandSender> command = commandManager.commands().iterator().next();
		assertEquals(4, command.components().size());
		assertEquals("key", command.components().get(2).name());
		assertEquals("value", command.components().get(3).name());
	}

	@Test
	void testCommandWithCooldown() {
		CommandDefinition.Cooldown cooldown = CommandDefinition.Cooldown.builder()
				.enabled(true)
				.duration(5)
				.group("test-group")
				.build();

		CommandDefinition definition = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("test"))
				.cooldown(cooldown)
				.build();

		registration.register(definition, context -> {});

		assertEquals(1, commandManager.commands().size());
	}

	@Test
	void testDisabledCommandNotRegistered() {
		CommandDefinition definition = CommandDefinition.builder()
				.enabled(false)
				.aliases(List.of("test"))
				.build();

		registration.register(definition, context -> {});

		assertTrue(commandManager.commands().isEmpty());
	}

	@Test
	void testInvalidAliasesThrowsException() {
		CommandDefinition emptyAliases = CommandDefinition.builder()
				.enabled(true)
				.aliases(Collections.emptyList())
				.build();

		assertThrows(IllegalArgumentException.class, () -> registration.register(emptyAliases, context -> {}));

		CommandDefinition nullAliases = CommandDefinition.builder()
				.enabled(true)
				.aliases(null)
				.build();

		assertThrows(IllegalArgumentException.class, () -> registration.register(nullAliases, context -> {}));
	}

	@Test
	void testRootCommandGetterAndSetter() {
		assertNull(registration.getRootCommand());
		registration.setRootCommand("intercept");
		assertEquals("intercept", registration.getRootCommand());
	}

	@Test
	void testUsageWithPlaceholderNotSubcommand() {
		CommandDefinition definition = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("test"))
				.usage("{command} something")
				.build();

		registration.register(definition, context -> {});

		Command<TestCommandSender> command = commandManager.commands().iterator().next();
		assertEquals(1, command.components().size());
		assertEquals("test", command.components().get(0).name());
	}

	private static final class TestCommandSender {
		private final Set<String> permissions = new HashSet<>();

		private TestCommandSender(String... permissions) {
			if (permissions != null) {
				Collections.addAll(this.permissions, permissions);
			}
		}

		private TestCommandSender() {
			this(new String[0]);
		}

		boolean hasPermission(String permission) {
			return permission == null || permission.isBlank() || permissions.contains(permission);
		}
	}
}