package org.figuramc.figura.server;

import org.figuramc.figura.server.events.Events;
import org.figuramc.figura.server.events.users.LoadPlayerDataEvent;
import org.figuramc.figura.server.events.users.UserLoadingExceptionEvent;
import org.figuramc.figura.server.utils.Either;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// TODO: Make FiguraUserManager also use CompletedFutures
public final class FiguraUserManager {
    private final FiguraServer parent;
    private final HashMap<UUID, Either<FiguraUser, FutureHandle>> users = new HashMap<>();
    private final LinkedList<UUID> expectedUsers = new LinkedList<>();

    public FiguraUserManager(FiguraServer parent) {
        this.parent = parent;
    }

    public CompletableFuture<FiguraUser> getUserOrNull(UUID playerUUID) {
        var either = users.get(playerUUID);
        if (either != null) return wrapHandle(either);
        return null;
    }

    public void onUserJoin(UUID player) {
        parent.sendHandshake(player);
    }

    public CompletableFuture<FiguraUser> getUser(UUID player) {
        return wrapHandle(users.computeIfAbsent(player, (p) -> Either.newB(
                new FutureHandle(player, startLoadingPlayerData(player)))
        ));
    }

    private CompletableFuture<FiguraUser> wrapHandle(Either<FiguraUser, FutureHandle> handle) {
        if (handle.isA()) return CompletableFuture.completedFuture(handle.a());
        FutureHandle futureHandle = handle.b();
        CompletableFuture<FiguraUser> future = futureHandle.future;
        if (future.isDone()) {
            try {
                FiguraUser user = future.join();
                handle.setA(user);
                return CompletableFuture.completedFuture(user);
            }
            catch (Exception e) {
                Events.call(new UserLoadingExceptionEvent(futureHandle.user, e));
            }
        }
        return null; // TODO
    }

    public CompletableFuture<Void> setupOnlinePlayer(UUID uuid, boolean allowPings, boolean allowAvatars, int s2cChunkSize) {
        CompletableFuture<FiguraUser> user = getUser(uuid);
        expectedUsers.remove(uuid); // This is called either way just to remove it in case if it was first time initialization
        return user.thenAcceptAsync(u -> {
            u.setOnline();
            u.update(allowPings, allowAvatars, s2cChunkSize);
        });
    }


    private CompletableFuture<FiguraUser> startLoadingPlayerData(UUID player) {
        LoadPlayerDataEvent playerDataEvent = Events.call(new LoadPlayerDataEvent(player));
        if (playerDataEvent.returned()) return playerDataEvent.returnValue();
        Path dataFile = parent.getUserdataFile(player);
        return CompletableFuture.supplyAsync(() -> FiguraUser.load(player, dataFile));
    }

    public void onUserLeave(UUID player) {
        users.computeIfPresent(player, (uuid, pl) -> {
            if (pl.isA()) {
                FiguraUser user = pl.a();
                user.save(parent.getUserdataFile(user.uuid()));
                user.setOffline();
                return pl;
            }
            else {
                pl.b().future.cancel(false);
                return null;
            }
        });
    }

    public void close() {
        for (var handle: users.values()) {
            if (handle.isA()) {
                FiguraUser pl = handle.a();
                pl.save(parent.getUserdataFile(pl.uuid()));
            }
        }
        users.clear();
    }

    public void expect(UUID user) {
        if (!expectedUsers.contains(user)) {
            expectedUsers.add(user);
        }
    }

    public boolean isExpected(UUID user) {
        return expectedUsers.contains(user);
    }

    private record FutureHandle(UUID user, CompletableFuture<FiguraUser> future) {}
}
