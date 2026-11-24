package com.origintools.app.service;

import android.os.RemoteException;

import java.io.IOException;

public final class ShellUserService extends IRemoteCommand.Stub {
    @Override
    public int execute(String[] command) throws RemoteException {
        if (command == null || command.length == 0) {
            return 2;
        }
        try {
            Process process = Runtime.getRuntime().exec(command);
            return process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return 130;
        } catch (IOException exception) {
            return 127;
        }
    }
}
