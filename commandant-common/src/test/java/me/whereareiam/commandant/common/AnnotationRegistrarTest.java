package me.whereareiam.commandant.common;

import me.whereareiam.commandant.common.registration.CommandDefinitionRegistration;
import me.whereareiam.commandant.common.registration.type.AnnotationRegistrar;
import me.whereareiam.commandant.model.CommandDefinition;
import me.whereareiam.commandant.registration.type.AnnotationCommandRegistrar;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationRegistrarTest {

	private CommandManager<TestCommandSender> commandManager;
	private AnnotationCommandRegistrar<TestCommandSender> registrar;

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

		CommandDefinitionRegistration<TestCommandSender> definitionRegistration = new CommandDefinitionRegistration<>(
				commandManager,
				TestCommandSender::getUuid,
				name -> null
		);
		this.registrar = new AnnotationRegistrar<>(definitionRegistration);
	}

	@Test
	void testAnnotatedCommandRegistration() {
		AnnotatedTestCommand annotated = new AnnotatedTestCommand();

		CommandDefinition definition = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("annotated"))
				.description("Annotated command")
				.usage("<value>")
				.build();

		registrar.registerAnnotatedCommand(definition, annotated, TestCommandSender.class);

		assertEquals(1, commandManager.commands().size());

		commandManager.commandExecutor().executeCommand(new TestCommandSender(), "annotated hello").join();

		assertTrue(annotated.invoked);
		assertEquals("hello", annotated.capturedValue);
	}

	@Test
	void testAnnotatedClassWithMultipleCommands() {
		MultiAnnotatedTestCommand annotated = new MultiAnnotatedTestCommand();

		CommandDefinition fooDefinition = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("foo"))
				.description("Foo command")
				.usage("<value>")
				.build();

		CommandDefinition barDefinition = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("bar"))
				.description("Bar command")
				.usage("<value>")
				.build();

		registrar.registerAnnotatedCommand(fooDefinition, annotated, TestCommandSender.class);
		commandManager.commandExecutor().executeCommand(new TestCommandSender(), "foo baz").join();
		assertEquals("baz", annotated.lastFooValue);
		assertNull(annotated.lastBarValue);

		registrar.registerAnnotatedCommand(barDefinition, annotated, TestCommandSender.class);
		commandManager.commandExecutor().executeCommand(new TestCommandSender(), "bar qux").join();
		assertEquals("baz", annotated.lastFooValue);
		assertEquals("qux", annotated.lastBarValue);
	}

	@Test
	void testAnnotatedCommandAppliesDefinitionMetadata() {
		AnnotatedTestCommand annotated = new AnnotatedTestCommand();

		CommandDefinition definition = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("annotated", "alias"))
				.permission("test.permission")
				.description("Annotated description")
				.usage("<value> [optional]")
				.arguments(Map.of(
						"value", "Value argument",
						"optional", "Optional argument"
				))
				.build();

		registrar.registerAnnotatedCommand(definition, annotated, TestCommandSender.class);

		assertEquals(1, commandManager.commands().size());
		Command<TestCommandSender> command = commandManager.commands().iterator().next();

		assertEquals("Annotated description", command.commandDescription().description().textDescription());
		assertEquals("test.permission", command.commandPermission().permissionString());
		assertEquals(3, command.components().size());
		assertEquals("annotated", command.components().get(0).name());

		CommandComponent<TestCommandSender> valueComponent = command.components().get(1);
		assertEquals("value", valueComponent.name());
		assertTrue(valueComponent.required());
		assertEquals("Value argument", valueComponent.description().textDescription());

		CommandComponent<TestCommandSender> optionalComponent = command.components().get(2);
		assertEquals("optional", optionalComponent.name());
		assertFalse(optionalComponent.required());
		assertEquals("Optional argument", optionalComponent.description().textDescription());

		assertNotNull(commandManager.commandTree().getNamedNode("alias"));
	}

	@Test
	void testAnnotatedCommandDisabledNotRegistered() {
		CommandDefinition definition = CommandDefinition.builder()
				.enabled(false)
				.aliases(List.of("annotated"))
				.usage("<value>")
				.build();

		registrar.registerAnnotatedCommand(definition, new AnnotatedTestCommand(), TestCommandSender.class);

		assertTrue(commandManager.commands().isEmpty());
	}

	private static final class AnnotatedTestCommand {
		private boolean invoked;
		private String capturedValue;

		@org.incendo.cloud.annotations.Command("annotated <value>")
		public void handle(
				@Argument("value") String value
		) {
			this.invoked = true;
			this.capturedValue = value;
		}
	}

	private static final class MultiAnnotatedTestCommand {
		private String lastFooValue;
		private String lastBarValue;

		@org.incendo.cloud.annotations.Command("foo <value>")
		public void handleFoo(@Argument("value") String value) {
			this.lastFooValue = value;
		}

		@org.incendo.cloud.annotations.Command("bar <value>")
		public void handleBar(@Argument("value") String value) {
			this.lastBarValue = value;
		}
	}
}
