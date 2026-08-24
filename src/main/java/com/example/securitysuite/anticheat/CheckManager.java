package com.example.securitysuite.anticheat;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.checks.combat.*;
import com.example.securitysuite.anticheat.checks.movement.*;
import com.example.securitysuite.anticheat.checks.packet.*;
import com.example.securitysuite.anticheat.checks.player.*;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central registry of every Check instance, plus the shared services
 * (CompensationService) checks depend on, and the entry point
 * {@link #report(Player, Check, com.example.securitysuite.model.CheckResult)}
 * that every listener funnels results through on the way to
 * ViolationManager/PunishmentManager/DiscordManager/alerts.
 */
public class CheckManager {

    private final SecurityPlugin plugin;
    private final CompensationService compensationService;
    private final Map<String, Check> checksById = new LinkedHashMap<>();

    // exposed directly because listeners need the typed overloads (extra event context)
    public ReachA reachA;
    public AimA aimA;
    public AutoClickerA autoClickerA;
    public CriticalsA criticalsA;
    public KillAuraA killAuraA;

    public SpeedA speedA;
    public FlyA flyA;
    public JesusA jesusA;
    public NoFallA noFallA;
    public StepA stepA;
    public GlideA glideA;

    public FastPlaceA fastPlaceA;
    public FastBreakA fastBreakA;
    public InventoryMoveA inventoryMoveA;
    public ScaffoldA scaffoldA;

    public BadPacketA badPacketA;
    public InvalidRotationA invalidRotationA;
    public MultiActionA multiActionA;

    public TimerA timerA;
    public NoSlowA noSlowA;
    public PhaseA phaseA;
    public FastLadderA fastLadderA;
    public InvalidSprintA invalidSprintA;

    public SnapAimA snapAimA;
    public VelocityA velocityA;

    public FastUseA fastUseA;
    public RegenA regenA;

    public CheckManager(SecurityPlugin plugin) {
        this.plugin = plugin;
        this.compensationService = new CompensationService(plugin);
    }

    public void load() {
        checksById.clear();

        reachA = register(new ReachA(plugin, compensationService));
        aimA = register(new AimA());
        autoClickerA = register(new AutoClickerA());
        criticalsA = register(new CriticalsA());
        killAuraA = register(new KillAuraA());

        speedA = register(new SpeedA(plugin, compensationService));
        flyA = register(new FlyA(plugin, compensationService));
        jesusA = register(new JesusA(plugin));
        noFallA = register(new NoFallA(plugin));
        stepA = register(new StepA(plugin));
        glideA = register(new GlideA());

        fastPlaceA = register(new FastPlaceA());
        fastBreakA = register(new FastBreakA());
        inventoryMoveA = register(new InventoryMoveA());
        scaffoldA = register(new ScaffoldA());

        badPacketA = register(new BadPacketA());
        invalidRotationA = register(new InvalidRotationA());
        multiActionA = register(new MultiActionA());

        timerA = register(new TimerA());
        noSlowA = register(new NoSlowA());
        phaseA = register(new PhaseA(plugin));
        fastLadderA = register(new FastLadderA(plugin));
        invalidSprintA = register(new InvalidSprintA());

        snapAimA = register(new SnapAimA());
        velocityA = register(new VelocityA());

        fastUseA = register(new FastUseA(plugin));
        regenA = register(new RegenA());
    }

    private <T extends Check> T register(T check) {
        checksById.put(check.id().toLowerCase(), check);
        return check;
    }

    public Map<String, Check> all() {
        return checksById;
    }

    public Check byId(String id) {
        return checksById.get(id.toLowerCase());
    }

    public CompensationService getCompensationService() {
        return compensationService;
    }

    /**
     * Every check result flows through here: it updates PlayerData VL,
     * persists the violation, feeds the PunishmentManager, and (if the
     * result was suspicious) triggers staff alerts / Discord.
     */
    public void report(Player player, Check check, com.example.securitysuite.model.CheckResult result) {
        if (!result.isSuspicious()) return;

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        data.addViolation(check.id(), result.getViolation(), result.getReason());

        plugin.getDatabaseManager().recordViolation(player.getUniqueId(), check.id(),
                data.getViolation(check.id()), result.getConfidence(), result.getReason());

        plugin.getViolationManager().onViolation(player, check, data, result);
    }

    /** Called every N seconds by a scheduler task to decay all players' VLs. */
    public void tickDecay() {
        if (!plugin.getConfigManager().getBoolean("anticheat.violation-decay.enabled", true)) return;
        double amount = plugin.getConfigManager().getDouble("anticheat.violation-decay.amount", 0.25);
        for (PlayerData data : plugin.getPlayerDataManager().all().values()) {
            for (Check check : checksById.values()) {
                data.decay(check.id(), amount);
            }
        }
    }
}
