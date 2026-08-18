import * as DocumentPicker from 'expo-document-picker';
import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import * as SQLite from 'expo-sqlite';

import type { Favorite } from '../src/types';

const DB_NAME = 'tukiwatch.db';

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

function getDb(): Promise<SQLite.SQLiteDatabase> {
  if (!dbPromise) {
    dbPromise = SQLite.openDatabaseAsync(DB_NAME).then(async (db) => {
      await db.execAsync(`
        PRAGMA journal_mode = WAL;
        CREATE TABLE IF NOT EXISTS favorites (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          streamer_name TEXT NOT NULL,
          original_url TEXT NOT NULL UNIQUE,
          created_at TEXT NOT NULL DEFAULT (datetime('now'))
        );
      `);
      return db;
    });
  }
  return dbPromise;
}

export async function getFavorites(): Promise<Favorite[]> {
  const db = await getDb();
  return db.getAllAsync<Favorite>(
    'SELECT id, streamer_name, original_url FROM favorites ORDER BY id'
  );
}

export async function isFavorite(originalUrl: string): Promise<boolean> {
  const db = await getDb();
  const row = await db.getFirstAsync<{ id: number }>(
    'SELECT id FROM favorites WHERE original_url = ?',
    originalUrl
  );
  return row !== null;
}

export async function addFavorite(streamerName: string, originalUrl: string): Promise<boolean> {
  if (await isFavorite(originalUrl)) return false;
  const db = await getDb();
  await db.runAsync('INSERT INTO favorites (streamer_name, original_url) VALUES (?, ?)', [
    streamerName,
    originalUrl,
  ]);
  return true;
}

export async function removeFavorite(id: number): Promise<void> {
  const db = await getDb();
  await db.runAsync('DELETE FROM favorites WHERE id = ?', id);
}

export async function exportFavorites(): Promise<boolean> {
  const favorites = await getFavorites();
  const payload = {
    app: 'tukiwatch',
    version: 1,
    exportedAt: new Date().toISOString(),
    favorites: favorites.map(({ streamer_name, original_url }) => ({
      streamer_name,
      original_url,
    })),
  };
  const file = new File(Paths.cache, `tukiwatch-favorites-${Date.now()}.json`);
  file.write(JSON.stringify(payload, null, 2));
  if (!(await Sharing.isAvailableAsync())) return false;
  await Sharing.shareAsync(file.uri, {
    mimeType: 'application/json',
    dialogTitle: 'Export TukiWatch favorites',
  });
  return true;
}

export async function importFavorites(): Promise<{ imported: number; skipped: number }> {
  const result = await DocumentPicker.getDocumentAsync({
    type: ['application/json', 'text/plain'],
    copyToCacheDirectory: true,
  });
  if (result.canceled) return { imported: 0, skipped: 0 };

  const asset = result.assets[0];
  const file = new File(asset.uri);
  const raw = await file.text();
  const parsed = JSON.parse(raw) as {
    favorites?: { streamer_name: string; original_url: string }[];
  };
  const items = Array.isArray(parsed) ? parsed : parsed.favorites;

  if (!Array.isArray(items)) {
    throw new Error('Invalid backup file: no favorites array found.');
  }

  let imported = 0;
  let skipped = 0;
  for (const item of items) {
    const name = item.streamer_name?.trim();
    const url = item.original_url?.trim();
    if (!name || !url) continue;
    if (await addFavorite(name, url)) {
      imported += 1;
    } else {
      skipped += 1;
    }
  }
  return { imported, skipped };
}
