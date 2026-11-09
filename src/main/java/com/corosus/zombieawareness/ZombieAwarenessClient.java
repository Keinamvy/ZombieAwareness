package com.corosus.zombieawareness;

public abstract class ZombieAwarenessClient {

    private static ZombieAwarenessClient instance;

    public static ZombieAwarenessClient instance() {
        return instance;
    }

    public ZombieAwarenessClient() {
        instance = this;
    }
}
