package com.netherfront.common.match;

import com.netherfront.common.util.NbtUtils2;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A Netherfront team. Deliberately independent of Reign of Nether's own faction
 * objects: the integration layer maps RoN factions onto these when available,
 * and the server owner assigns them by hand when it is not (section 39).
 */
public final class MatchTeam {
    /** Sentinel id for everything not owned by a player team. */
    public static final String NEUTRAL = "neutral";

    private final String id;
    private String displayName;
    private ChatFormatting color;
    /** Colourblind-safe glyph, since section 44 forbids colour-only ownership cues. */
    private String symbol;
    private final Set<UUID> members = new LinkedHashSet<>();
    private String ronFactionHint = "";
    private int dominationScore;

    public MatchTeam(String id, String displayName, ChatFormatting color, String symbol) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.symbol = symbol;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public ChatFormatting color() {
        return color;
    }

    public void setColor(ChatFormatting color) {
        this.color = color;
    }

    public String symbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Set<UUID> members() {
        return members;
    }

    public boolean contains(UUID player) {
        return members.contains(player);
    }

    /**
     * Free-text name of the Reign of Nether faction this team plays, used by the
     * integration layer to bind RoN factions to Netherfront teams.
     */
    public String ronFactionHint() {
        return ronFactionHint;
    }

    public void setRonFactionHint(String hint) {
        this.ronFactionHint = hint == null ? "" : hint;
    }

    public int dominationScore() {
        return dominationScore;
    }

    public void addDominationScore(int amount) {
        this.dominationScore = Math.max(0, this.dominationScore + amount);
    }

    public void setDominationScore(int score) {
        this.dominationScore = Math.max(0, score);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", displayName);
        tag.putString("color", color.getName());
        tag.putString("symbol", symbol);
        tag.putString("ron", ronFactionHint);
        tag.putInt("score", dominationScore);
        NbtUtils2.putUuidList(tag, "members", members);
        return tag;
    }

    public static MatchTeam load(CompoundTag tag) {
        ChatFormatting color = ChatFormatting.getByName(tag.getString("color"));
        MatchTeam team = new MatchTeam(
                tag.getString("id"),
                tag.getString("name"),
                color == null ? ChatFormatting.WHITE : color,
                tag.getString("symbol"));
        team.ronFactionHint = tag.getString("ron");
        team.dominationScore = tag.getInt("score");
        team.members.addAll(NbtUtils2.getUuidList(tag, "members"));
        return team;
    }
}
