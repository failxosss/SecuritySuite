# SecuritySuite

Combined **AntiVPN/AntiProxy** + modular **AntiCheat** plugin for Paper (Java 21+, Paper 1.20.4 API).

This README documents installation, configuration, permissions, commands, IP-intelligence
provider setup, database setup, Discord webhook setup, troubleshooting, and an explanation
of every AntiCheat check and its known limitations.

---

## 1. Installation

Requirements:
- Java 21+
- Paper 1.20.4 (or a Paper fork that maintains API compatibility)
- Maven 3.9+ to build from source

Build:

```bash
mvn clean package
```

The shaded jar is produced at `target/SecuritySuite-1.0.0.jar`. Copy it into your server's
`plugins/` folder and restart (or `/reload`, though a full restart is recommended).

On first start the plugin generates, inside `plugins/SecuritySuite/`:
- `config.yml` — all tunables (see section 4)
- `messages.yml` — every user-facing string
- `secrets.yml` — API keys, DB password, Discord webhook URL (see section 3, "Secrets")
- `securitysuite.db` — SQLite database (if `database.type: SQLITE`, the default)
- `.salt` — a randomly generated salt used only for IP hashing (see section 8, Privacy)

---

## 2. Quick start

1. Start the server once to generate the default files, then stop it.
2. Open `plugins/SecuritySuite/secrets.yml` and, if you want more than the free `ip-api.com`
   provider, add your `ipqualityscore` API key (see section 5).
3. Review `plugins/SecuritySuite/config.yml` — at minimum, decide your `antivpn.actions` and
   `anticheat.punishments.thresholds`.
4. Start the server. Use `/security` in-game (requires `security.admin`) to open the admin GUI.

---

## 3. Secrets (API keys, DB password, Discord webhook)

**Never put real API keys, database passwords, or webhook URLs directly in `config.yml`.**
Use `plugins/SecuritySuite/secrets.yml` instead — it is loaded separately, values there always
override the matching `config.yml` value, and its contents are never written to the console or
log files by any code path in this plugin.

```yaml
providers:
  ipqualityscore:
    api-key: "your-key-here"
  custom:
    api-key: ""
discord:
  webhook-url: "https://discord.com/api/webhooks/..."
database:
  mysql:
    password: "your-db-password"
```

If you commit your server config to version control, add `secrets.yml` to `.gitignore`.

---

## 4. Configuration overview (`config.yml`)

The generated `config.yml` is heavily commented; the major sections are:

| Section | Purpose |
|---|---|
| `antivpn` | providers, cache, whitelist, scoring weights, thresholds, actions |
| `anticheat` | check category toggles, decay, compensation (ping/TPS/lag), evidence, punishments, alerts |
| `performance` | async processing flags, soft time budgets |
| `discord` | webhook toggle, per-feature send flags, minimum VL to report |
| `database` | SQLITE / MYSQL / MARIADB, connection pool sizing |
| `privacy` | IP storage/hashing/retention |
| `gui` | title/size for `/security` |

All messages (kick reasons, alerts, command output) live in `messages.yml`, not `config.yml`.

---

## 5. AntiVPN / IP-intelligence provider setup

SecuritySuite queries providers **in the order listed in `antivpn.providers.order`**, using the
first one that returns a successful result. Three providers ship out of the box:

### `ip-api` (default, enabled, no key required)
Uses the free HTTP endpoint at `ip-api.com` (rate-limited to 45 requests/minute per source IP
on the free tier). Gives country, ISP, org, ASN, and a combined proxy/hosting flag. **It does
not provide a dedicated Tor flag** — see the "Known limitations" note below.

### `ipqualityscore` (disabled by default, requires an API key)
Set `antivpn.providers.ipqualityscore.enabled: true` in `config.yml` and put your key in
`secrets.yml` under `providers.ipqualityscore.api-key`. This provider *does* return a real Tor
flag and a fraud/abuse score, so it's the recommended primary provider if you're willing to pay
for (or trial) a key.

### `custom` (disabled by default)
For any other JSON IP-intelligence API. Configure `base-url` (with `%IP%` / `%KEY%`
placeholders), and map your API's field names to SecuritySuite's expected fields under
`antivpn.providers.custom.field-map`. This only works for flat JSON responses — if your
provider returns nested objects, write a dedicated `IpProvider` implementation instead (see
`IpProvider.java` — it's a small interface).

### Known limitation: Tor exit-node detection
Reliable Tor detection needs either a provider that supplies it (IPQualityScore does) or a
regularly-refreshed Tor bulk exit-node list, which is **not bundled** with this plugin (shipping
a stale list would be actively misleading). If no configured provider reports Tor, the `tor`
field simply stays `false` rather than being guessed — this is a deliberate, documented
fallback, not a silent failure.

### Known limitation: datacenter/ASN classification
Without a provider that returns an explicit hosting/datacenter flag, SecuritySuite falls back to
a coarse keyword match against the ASN/org string (`AntiVPN.RiskScorer#isDatacenterAsn`) for
well-known cloud/hosting providers (AWS, GCP, Azure, DigitalOcean, OVH, Hetzner, Linode, Vultr).
This is a heuristic, not authoritative ASN-type data — configure a provider with a proper
hosting flag (e.g. IPQualityScore) for accurate results.

---

## 6. Risk scoring

Each detection signal adds points (fully configurable under `antivpn.scoring`):

```
VPN detected      +40      Datacenter ASN   +20
Proxy detected     +35      Known abuse IP   +50
Hosting            +25      Tor              +70
```

The total (capped at 100) maps to a rating via `antivpn.thresholds`:

```
0–29   LOW        60–79  HIGH
30–59  MEDIUM      80–100 CRITICAL
```

Each rating threshold is a lower-bound (`score >= threshold`). Scoring is implemented as a pure
function in `RiskScorer.java` with no Bukkit dependency, specifically so it's fully unit-testable
(see `RiskScorerTest.java`) independent of a running server.

---

## 7. AntiCheat design

Checks never punish directly. Every check returns a `CheckResult` (`suspicious`, `confidence`,
`violation`, `reason`). Results flow:

```
Check.evaluate() -> CheckManager.report() -> PlayerData (VL + evidence)
                                            -> ViolationManager (staff alert + Discord)
                                            -> PunishmentManager (threshold + multi-check guard)
```

**Punishments require both** a violation-level threshold (`anticheat.punishments.thresholds`)
**and** contributions from `min-distinct-checks` different checks within
`time-window-seconds` before anything at or above KICK fires. This is the guard against a
single noisy check — or one lag spike — banning an innocent player. ALERT/WARN-tier actions
don't require the multi-check guard since they're informational/reversible.

Violation levels decay continuously (`anticheat.violation-decay`) so old suspicion fades if the
player's behaviour becomes clean again.

### Compensation (false-positive protection)
`CompensationService` centralizes:
- **Ping compensation** — timing-sensitive checks (ReachA) get extra tolerance scaled to the
  player's ping, capped at `max-compensation-ms`.
- **TPS compensation** — when server TPS drops below `grace-tps`, violation thresholds are
  multiplied by `leniency-multiplier` server-wide.
- **Lag-spike detection** — a tick-delta above `spike-threshold-ms` is flagged internally so
  movement checks can discard the sample instead of flagging it.

Individual checks also explicitly exclude legitimate mechanics: knockback, velocity, teleports,
ice, water, ladders, vehicles, elytra, potion effects (Speed/Jump Boost/Slow Falling/Levitation/
Mining Fatigue/Haste), soul sand/soul speed, frost walker, hay bales/slime blocks/powder snow for
fall damage, and creative-mode instant-mine/fly.

### Registered checks

**Combat**
| Check | What it detects | Key exclusions |
|---|---|---|
| `ReachA` | Melee hits beyond the vanilla reach envelope | ping-scaled tolerance, creative reach |
| `AimA` | Rotation deltas that are suspiciously identical across consecutive ticks (GCD/aim-assist pattern) | requires actual movement present |
| `AutoClickerA` | CPS above human capability, or high CPS with unnaturally low click-timing variance | requires both extreme CPS or low variance, not either alone at moderate CPS |
| `CriticalsA` | Critical-hit damage/particle claimed while not falling/airborne | ladder, water, vehicle |
| `KillAuraA` | Attacks landed at an angle far outside the player's crosshair/look direction | — |
| `SnapAimA` | A single-tick rotation delta immediately before an attack, too large for mouse input | multi-tick flicks (delta measured tick-to-tick, not summed) |
| `VelocityA` | Applied knockback the victim's position doesn't follow through on (anti-knockback) | negligible knockback, 4-tick settle window before judging |

**Movement**
| Check | What it detects | Key exclusions |
|---|---|---|
| `SpeedA` | Horizontal speed beyond what sprint/potions/ice/soul-speed can explain | teleport, knockback, velocity, elytra, vehicle grace windows |
| `FlyA` | Sustained hover or unexplained upward motion | flight-enabled, levitation, water/ladder/vehicle/elytra, recent knockback/teleport |
| `JesusA` | Standing on unconverted water/lava surfaces | Frost Walker ice conversion, boats |
| `NoFallA` | Landing after a damage-eligible fall with no damage event | Slow Falling, elytra, hay/slime/water/powder-snow landings |
| `StepA` | Instant >0.6-block step-up while grounded without a jump arc | ladder, water, elytra, vehicle, teleport/velocity grace |
| `GlideA` | Elytra-style glide trajectory without an elytra active | vehicle, water, on-ground |
| `TimerA` | Movement-packet rate sustained above 20/s (client tick-rate manipulation) | measured over a 2s window, not packet-to-packet, to absorb post-lag bursts |
| `NoSlowA` | Full-speed movement while an item is raised (eating/drinking/blocking) | gliding, in vehicle, in water |
| `PhaseA` | Movement path sampled through a solid, non-passable block (noclip) | teleports/long segments excluded; point-sampled, not a full hitbox sweep |
| `FastLadderA` | Upward climb speed on ladder/vine/scaffolding above the vanilla cap | descending (always fast/legit), teleport/velocity/knockback grace |
| `InvalidSprintA` | Sprinting while sneaking/blind/item-raised/very low hunger | creative, spectator; low-hunger alone scored at reduced confidence (see class Javadoc) |

**Player**
| Check | What it detects | Key exclusions |
|---|---|---|
| `FastPlaceA` | Block placement rate above a generous human ceiling | — |
| `FastBreakA` | Block break time below an estimated minimum for tool/block/enchant/effect | creative instant-mine (see "Known limitation" below) |
| `InventoryMoveA` | Movement recorded while a container-type inventory is open | brief close-race window tolerated (requires a short burst, not a single sample) |
| `ScaffoldA` | Auto-bridge signature: block placed under feet + sharp downward pitch snap | — |
| `FastUseA` | Item eaten/drunk faster than the vanilla ~32-tick use animation | only judged when a hand-raise start was actually observed (no guessing) |
| `RegenA` | Natural-regen-reason healing beyond what saturation ticking can produce | only the `REGEN` reason is counted — golden apples/potions/etc. have their own reasons and aren't included |

**Packet**
| Check | What it detects | Key exclusions |
|---|---|---|
| `InvalidRotationA` | Pitch outside [-90,90] or NaN/Infinite rotation values | — |
| `BadPacketA` | NaN/Infinite position, movement while dead, event-flood rate | see "Known limitation" below |
| `MultiActionA` | Block break + block place + attack all landing in the same server tick | requires all three, not any single fast action |

### Known limitation: `FastBreakA` accuracy
Vanilla's true break-speed formula (tool tier, block hardness, efficiency level, haste, mining
fatigue, aqua affinity, on-ground vs. in-water/no-jump penalties) is not fully exposed by the
Paper API. `FastBreakA` uses a documented, deliberately conservative approximation
(`MIN_TIME_SAFETY_FACTOR = 0.55` of the estimate) so it only catches clearly-too-fast breaks
rather than marginal ones — a false-negative-leaning trade-off, chosen deliberately over
false-positives. See the class Javadoc in `FastBreakA.java`.

### Known limitation: `BadPacketA` / true packet-level validation
A genuine "bad packet" check — validating raw packet structure, sequence/order, or malformed
NBT — requires intercepting packets **below** the Bukkit event API (e.g. via **ProtocolLib** or a
Netty channel injector). Plain Paper/Bukkit does not expose raw incoming packets. `BadPacketA`
implements the subset that **is** reliably possible from Bukkit-level events alone (NaN/Infinite
coordinates, movement while dead, a coarse event-flood rate) and is documented as such in the
class itself. If you need full packet-level validation, add ProtocolLib as a dependency and hook
its `PacketListener`; the check-registration, violation, and evidence plumbing here will work
unchanged with a new packet-tier check class.

---

## 8. Privacy

```yaml
privacy:
  store-ip: false          # if false, no IP is ever written to the database
  hash-ip: true             # if store-ip is true, store a salted SHA-256 hash instead of plaintext
  data-retention-days: 30   # 0 = keep forever; otherwise an hourly task purges old rows
```

The salt used for hashing is generated once per install (`plugins/SecuritySuite/.salt`) and never
logged. If you need IPs to be genuinely unrecoverable, leave `store-ip: false` — the AntiVPN
pipeline still performs its lookup and caches the *result* in memory (governed by
`antivpn.cache.ttl-seconds`), it simply never persists the address itself.

---

## 9. Database

Supports **SQLite** (default, zero setup), **MySQL**, and **MariaDB**, via HikariCP connection
pooling. All queries run on an async executor — nothing touches the main server thread.

```yaml
database:
  type: SQLITE   # or MYSQL / MARIADB
  sqlite:
    file: "securitysuite.db"
  mysql:
    host: "localhost"
    port: 3306
    database: "securitysuite"
    username: "securitysuite"
    password: ""   # put the real value in secrets.yml -> database.mysql.password
    use-ssl: false
```

Tables created automatically on first connect: `players`, `antivpn_detections`, `violations`,
`punishments`. SQLite's pool is intentionally capped at 1 connection (SQLite is effectively
single-writer); MySQL/MariaDB use the configured pool size.

---

## 10. Discord webhook

```yaml
discord:
  enabled: true
  webhook-url: ""   # put the real URL in secrets.yml -> discord.webhook-url instead
  send-antivpn-events: true
  send-anticheat-events: true
  anticheat-min-vl: 15   # only send AntiCheat events once VL crosses this, to avoid spam
  username: "SecuritySuite"
```

Delivery is fully async (`java.net.http.HttpClient`) and failures are logged at `fine` level
without blocking or retrying indefinitely — a webhook outage will never affect gameplay.

---

## 11. Permissions

| Permission | Default | Grants |
|---|---|---|
| `security.admin` | op | `/security`, GUI, reload, stats, debug |
| `security.antivpn` | op | `/antivpn` subcommands |
| `security.anticheat` | op | `/anticheat` (`/ac`) subcommands |
| `security.alerts` | op | Receive staff alerts (AntiCheat + AntiVPN) |
| `security.verbose` | op | Receive verbose/debug-level AntiCheat output |
| `antivpn.bypass` | false | Bypass AntiVPN checks entirely |
| `security.bypass` | false | Bypass both AntiVPN and AntiCheat entirely |
| `security.*` | op | All of the above |

---

## 12. Commands

```
/security                      Open the admin GUI
/security reload               Reload config.yml / messages.yml / cache / providers
/security stats                Quick stats summary
/security performance          Memory, cache size, avg check/API/DB timings
/security debug                Alias of performance
/security test <player>        Toggle AntiCheat test mode (simulates punishments, applies none)

/antivpn                       Help
/antivpn check <player>        Run a fresh AntiVPN lookup and print the result
/antivpn info <player>         Alias of check
/antivpn clearcache            Clear the IP lookup cache

/anticheat  (alias /ac)        Help
/ac alerts                     Toggle receiving AntiCheat alerts
/ac verbose                    Toggle verbose output
/ac info <player>              Show a player's current + peak violation levels per check
/ac violations <player>        Same as info
/ac reset <player>             Reset all violation levels for a player
/ac checks                     List every registered check, grouped by category, with on/off state
/ac reload                     Reload config/messages
```

---

## 13. Testing

Unit tests (`mvn test`) cover the pieces that don't require a running Bukkit server:
- `RiskScorerTest` — AntiVPN scoring math, threshold boundaries, capping at 100, fail-open behaviour
- `PlayerDataTest` — violation accumulation, decay (including the "never negative" floor), peak
  tracking, evidence buffer bounding, per-check reset vs. reset-all, knockback grace window
- `CheckResultTest` — confidence/violation clamping, null-reason handling

**Honest limitation:** managers that depend on a live `Player`/`Server` (AntiVPNManager's join
pipeline, CheckManager's listeners, PunishmentManager's ban/kick calls, DatabaseManager's actual
SQL execution) are not covered by these plain-JUnit tests, since exercising them meaningfully
needs either a running Paper server or a mocking framework like MockBukkit — neither is wired up
here. If you extend this project, MockBukkit is the recommended path for integration-level tests
of the listener/manager wiring.

`/security test <player>` provides a live, in-game equivalent: it runs the full AntiCheat pipeline
(checks → violations → punishment threshold evaluation) but replaces the final punishment action
with a staff-only notice, so you can sanity-check tuning against real players without risking a
false-positive kick/ban.

---

## 14. Troubleshooting

**"AntiVPN never flags anyone."**
Check `/security performance` for the average API lookup time — 0ms with no errors in the log
usually means no provider is enabled. `ip-api` is enabled by default and needs no key; confirm
`antivpn.enabled: true` and `antivpn.providers.ip-api.enabled: true`.

**"AntiVPN kicks legitimate players."**
Lower `antivpn.actions.*` from `KICK`/`BAN` to `NOTIFY` while tuning, check
`antivpn.scoring` weights against your player base's ISPs (business/university ISPs sometimes
route through IPs that get flagged as hosting), and add trusted IPs/players to
`antivpn.whitelist`.

**"A player got kicked/banned by AntiCheat for something that looks legitimate."**
Use `/ac info <player>` to see which checks contributed and their evidence buffer
(`PlayerData#getEvidence`, surfaced through the alert `reason` field). If it's a specific
mechanic not already excluded, that's a bug — please open an issue with the check name and
evidence line.

**"Discord messages aren't arriving."**
Confirm `discord.enabled: true` and that `secrets.yml` → `discord.webhook-url` is set (values in
`secrets.yml` always win over `config.yml`). Check the log at `fine`/debug level for delivery
failures — SecuritySuite never retries indefinitely so a bad URL will simply log once per event.

**"MySQL connection fails on startup."**
Confirm `secrets.yml` → `database.mysql.password` is set (not `config.yml`), and that
`database.mysql.use-ssl` matches your server's actual TLS configuration.

---

## 15. Project layout

```
pom.xml
src/main/resources/plugin.yml
src/main/resources/config.yml
src/main/resources/messages.yml
src/main/resources/secrets.example.yml
src/main/java/com/example/securitysuite/
 ├── SecurityPlugin.java              main class, wiring
 ├── PerformanceStats.java
 ├── config/         ConfigManager, MessageManager
 ├── database/        DatabaseManager, CacheManager
 ├── discord/          DiscordManager
 ├── antivpn/          AntiVPNManager, RiskScorer, AntiVpnPunishmentDispatcher, provider/
 ├── anticheat/        Check, PlayerData, PlayerDataManager, CheckManager,
 │                      CompensationService, ViolationManager, PunishmentManager,
 │                      checks/{combat,movement,player,packet}/
 ├── listener/         PlayerConnectionListener, MovementListener, CombatListener,
 │                      InventoryListener, WorldStateListener
 ├── command/          SecurityCommand, AntiVpnCommand, AntiCheatCommand
 ├── gui/              SecurityGui
 ├── model/            CheckResult, IpLookupResult, RiskAssessment
 └── util/             AsyncHttp
src/test/java/com/example/securitysuite/
 ├── RiskScorerTest.java
 ├── PlayerDataTest.java
 └── CheckResultTest.java
```

---

## 16. License enforcement

SecuritySuite requires a license key (`license.key` in config.yml). The plugin does nothing
at all - no listeners, commands, DB connection, AntiVPN, or AntiCheat - until a valid key is
present and the server is restarted. 1000 keys are pre-generated; only their SHA-256 hashes
are compiled into the jar (`LicenseKeyHashes.java`), so decompiling the jar does not recover
usable plaintext keys.

**Honest limit:** this is an offline, client-side check, like every purely offline license
system in a distributed compiled jar. It stops casual key sharing/extraction. It cannot stop
someone with enough skill from patching the compiled bytecode to skip the check outright -
no offline check can. Real enforcement against a technical adversary would require the plugin
phoning home to a server you control at startup (a separate, larger feature, not implemented
here).

---

## 17. A note on scope and honesty

Per the project's own requirement to flag anything that can't be reliably implemented rather than
pretending it works, three things are explicitly called out above rather than silently
approximated:

1. **Tor exit-node detection** requires a live provider or a maintained exit-list; without one,
   the `tor` flag stays `false`.
2. **FastBreakA** uses a conservative break-time estimate, not Minecraft's exact internal formula.
3. **BadPacketA** implements only the Bukkit-API-reachable subset of "bad packet" detection; true
   packet-level validation needs ProtocolLib or a Netty injector, which is not bundled.

Everything else in this README describes real, implemented, wired-up behaviour in the source
tree — not aspirational design.

### Where this still falls short of Grim/Vulcan-class anticheats

27 checks is more checks than this project shipped with, but check *count* isn't the thing that
actually separates a Bukkit-event-level anticheat from Grim/Vulcan/NCP-class ones. What they have
that this project doesn't:

- **Packet-level movement prediction.** Grim/Vulcan reimplement the client's own movement
  simulation server-side, tick by tick, and compare predicted vs. reported position with
  sub-block precision. This project reads `PlayerMoveEvent`/rotation/state at the Bukkit-event
  layer, which is coarser and easier to evade with a well-tuned client-side spoof.
- **Full packet interception.** `BadPacketA`/`MultiActionA` work from Bukkit events, not raw
  packets — see the `BadPacketA` known-limitation above. A ProtocolLib or Netty-injector layer
  would materially raise the ceiling here.
- **Anti-evasion / obfuscation resistance.** Mature anticheats specifically harden against
  known bypass techniques (blink/rotation-lag exploits, packet-order manipulation) that this
  project's checks don't defend against.
- **Years of tuning against real bypass clients.** The thresholds here are reasoned estimates
  with documented margins, not values tuned against actual cheat-client traffic at scale.

None of the new checks above change that picture — they add coverage in categories (timer, phase,
knockback compliance, item-use timing, natural regen abuse, same-tick multi-action) that weren't
tested before, at the same Bukkit-event fidelity as the rest of the project.
