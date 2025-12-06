package maestro.command.defaults;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.datatypes.EntityClassById;
import maestro.command.datatypes.IDatatypeFor;
import maestro.command.datatypes.NearbyPlayer;
import maestro.command.exception.CommandException;
import maestro.command.helpers.TabCompleteHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class FollowCommand extends Command {

    public FollowCommand(Agent agent) {
        super(agent, "follow");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);
        FollowGroup group;
        FollowList list;
        List<Entity> entities = new ArrayList<>();
        List<EntityType> classes = new ArrayList<>();
        if (args.hasExactlyOne()) {
            agent.getFollowTask().follow((group = args.getEnum(FollowGroup.class)).filter);
        } else {
            args.requireMin(2);
            group = null;
            list = args.getEnum(FollowList.class);
            while (args.hasAny()) {
                Object gotten = args.getDatatypeFor(list.datatype);
                if (gotten instanceof EntityType) {
                    classes.add((EntityType) gotten);
                } else if (gotten != null) {
                    entities.add((Entity) gotten);
                }
            }

            agent.getFollowTask()
                    .follow(
                            classes.isEmpty()
                                    ? entities::contains
                                    : e -> classes.stream().anyMatch(c -> e.getType().equals(c)));
        }
        if (group != null) {
            log.atInfo()
                    .addKeyValue("group", group.name().toLowerCase(Locale.US))
                    .log("Following all entities in group");
        } else {
            if (classes.isEmpty()) {
                if (entities.isEmpty()) throw new NoEntitiesException();
                log.atInfo()
                        .addKeyValue("count", entities.size())
                        .log("Following specific entities");
                entities.stream()
                        .map(Entity::toString)
                        .forEach(
                                entity ->
                                        log.atInfo()
                                                .addKeyValue("entity", entity)
                                                .log("Target entity"));
            } else {
                log.atInfo().addKeyValue("count", classes.size()).log("Following entity types");
                classes.stream()
                        .map(BuiltInRegistries.ENTITY_TYPE::getKey)
                        .map(Objects::requireNonNull)
                        .map(ResourceLocation::toString)
                        .forEach(type -> log.atInfo().addKeyValue("type", type).log("Target type"));
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                            .append(FollowGroup.class)
                            .append(FollowList.class)
                            .filterPrefix(args.getString())
                            .stream();
        } else {
            IDatatypeFor followType;
            try {
                followType = args.getEnum(FollowList.class).datatype;
            } catch (NullPointerException e) {
                return Stream.empty();
            }
            while (args.has(2)) {
                if (args.peekDatatypeOrNull(followType) == null) {
                    return Stream.empty();
                }
                args.get();
            }
            return args.tabCompleteDatatype(followType);
        }
    }

    @Override
    public String getShortDesc() {
        return "Follow entity things";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The follow command tells Maestro to follow certain kinds of entities.",
                "",
                "Usage:",
                "> follow entities - Follows all entities.",
                "> follow entity <entity1> <entity2> <...> - Follow certain entities (for example"
                        + " 'skeleton', 'horse' etc.)",
                "> follow players - Follow players",
                "> follow player <username1> <username2> <...> - Follow certain players");
    }

    @SuppressWarnings("ImmutableEnumChecker")
    private enum FollowGroup {
        ENTITIES(LivingEntity.class::isInstance),
        PLAYERS(Player.class::isInstance); /* ,
        FRIENDLY(entity -> entity.getAttackTarget() != HELPER.mc.player),
        HOSTILE(FRIENDLY.filter.negate()); */
        final Predicate<Entity> filter;

        FollowGroup(Predicate<Entity> filter) {
            this.filter = filter;
        }
    }

    @SuppressWarnings("ImmutableEnumChecker")
    private enum FollowList {
        ENTITY(EntityClassById.INSTANCE),
        PLAYER(NearbyPlayer.INSTANCE);

        final IDatatypeFor datatype;

        FollowList(IDatatypeFor datatype) {
            this.datatype = datatype;
        }
    }

    public static class NoEntitiesException extends CommandException.ErrorMessage {

        protected NoEntitiesException() {
            super("No valid entities in range!");
        }
    }
}
