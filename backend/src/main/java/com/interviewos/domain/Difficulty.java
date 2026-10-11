package com.interviewos.domain;

public enum Difficulty {
    EASY(1), MEDIUM(2), HARD(3);

    private final int level;

    Difficulty(int level) { this.level = level; }

    public int level() { return level; }

    public Difficulty up() { return this == HARD ? HARD : values()[ordinal() + 1]; }

    public Difficulty down() { return this == EASY ? EASY : values()[ordinal() - 1]; }
}
