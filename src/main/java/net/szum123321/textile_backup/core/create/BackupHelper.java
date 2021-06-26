/*
    A simple backup mod for Fabric
    Copyright (C) 2020  Szum123321

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package net.szum123321.textile_backup.core.create;

import net.minecraft.network.MessageType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.szum123321.textile_backup.Statics;
import net.szum123321.textile_backup.core.ActionInitiator;
import net.szum123321.textile_backup.core.Utilities;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

public class BackupHelper {
	public static Runnable create(BackupContext ctx) {
		notifyPlayers(ctx);

		StringBuilder builder = new StringBuilder();

		builder.append("Backup started ");

		builder.append(ctx.getInitiator().getPrefix());

		if(ctx.startedByPlayer()) {
			builder.append(ctx.getCommandSource().getDisplayName().getString());
		} else {
			builder.append(ctx.getInitiator().getName());
		}

		builder.append(" on: ");
		builder.append(Utilities.getDateTimeFormatter().format(LocalDateTime.now()));

		Statics.LOGGER.info(builder.toString());

		if (ctx.shouldSave()) {
			Statics.LOGGER.sendInfoAL(ctx, "Saving server...");

			ctx.getServer().getPlayerManager().saveAllPlayerData();

			try {
				ctx.getServer().save(false, true, true);
			} catch (Exception e) {
				Statics.LOGGER.sendErrorAL(ctx,"An exception occurred when trying to save the world!");
			}
		}

		return new MakeBackupRunnable(ctx);
	}

	private static void notifyPlayers(BackupContext ctx) {
		MutableText message = Statics.LOGGER.getPrefixText();
		message.append(new LiteralText("Warning! Server backup will begin shortly. You may experience some lag.").formatted(Formatting.WHITE));

		UUID uuid;

		if(ctx.getInitiator().equals(ActionInitiator.Player) && ctx.getCommandSource().getEntity() != null)
			uuid = ctx.getCommandSource().getEntity().getUuid();
		else uuid = Util.NIL_UUID;

		ctx.getServer().getPlayerManager().broadcastChatMessage(
				message,
				MessageType.SYSTEM,
				uuid
		);
	}

	public static int executeFileLimit(ServerCommandSource ctx, String worldName) {
		File root = Utilities.getBackupRootPath(worldName);
		int deletedFiles = 0;

		if (root.isDirectory() && root.exists() && root.listFiles() != null) {
			if (Statics.CONFIG.maxAge > 0) { // delete files older that configured
				final LocalDateTime now = LocalDateTime.now();

				deletedFiles += Arrays.stream(root.listFiles())
						.filter(Utilities::isValidBackup)// We check if we can get file's creation date so that the next line won't throw an exception
						.filter(f -> now.toEpochSecond(ZoneOffset.UTC) - Utilities.getFileCreationTime(f).get().toEpochSecond(ZoneOffset.UTC) > Statics.CONFIG.maxAge)
						.map(f -> deleteFile(f, ctx))
						.filter(b -> b).count(); //a bit awkward
			}

			if (Statics.CONFIG.backupsToKeep > 0 && root.listFiles().length > Statics.CONFIG.backupsToKeep) {
				deletedFiles += Arrays.stream(root.listFiles())
						.filter(Utilities::isValidBackup)
						.sorted(Comparator.comparing(f -> Utilities.getFileCreationTime((File) f).get()).reversed())
						.skip(Statics.CONFIG.backupsToKeep)
						.map(f -> deleteFile(f, ctx))
						.filter(b -> b).count();
			}

			if (Statics.CONFIG.maxSize > 0 && FileUtils.sizeOfDirectory(root) / 1024 > Statics.CONFIG.maxSize) {
				deletedFiles += Arrays.stream(root.listFiles())
						.filter(Utilities::isValidBackup)
						.sorted(Comparator.comparing(f -> Utilities.getFileCreationTime(f).get()))
						.takeWhile(f -> FileUtils.sizeOfDirectory(root) / 1024 > Statics.CONFIG.maxSize)
						.map(f -> deleteFile(f, ctx))
						.filter(b -> b).count();
			}
		}

		return deletedFiles;
	}

	private static boolean deleteFile(File f, ServerCommandSource ctx) {
		if(Statics.untouchableFile.isEmpty()|| !Statics.untouchableFile.get().equals(f)) {
			if(f.delete()) {
				Statics.LOGGER.sendInfoAL(ctx, "Deleting: {}", f.getName());
				return true;
			} else {
				Statics.LOGGER.sendErrorAL(ctx, "Something went wrong while deleting: {}.", f.getName());
			}
		}

		return false;
	}
}