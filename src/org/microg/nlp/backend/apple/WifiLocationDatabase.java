package org.microg.nlp.backend.apple;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

public class WifiLocationDatabase extends SQLiteOpenHelper {
	private static final int DATABASE_VERSION = 2;

	private static final String TABLE_NAME = "location";

	/**
	 * The field containing the BSSID of a wifi network, which is the Mac address of the Access Point.
	 */
	private static final String FIELD_MAC = "mac";
	private static final String FIELD_LATITUDE = "latitude";
	private static final String FIELD_LONGITUDE = "longitude";
	private static final String FIELD_ALTITUDE = "altitude";
	private static final String FIELD_ACCURACY = "accuracy";
	/**
	 * The field containing the unix timestamp in milliseconds of retrieval for this location.
	 */
	private static final String FIELD_TIME = "time";
	/**
	 * The field containing the unix timestamp in milliseconds of the last verification for this location
	 */
	private static final String FIELD_VERIFIED = "verified";

	private static final String SQL_CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" +
			FIELD_MAC + " TEXT PRIMARY KEY, " +
			FIELD_LATITUDE + " REAL, " +
			FIELD_LONGITUDE + " REAL, " +
			FIELD_ALTITUDE + " REAL, " +
			FIELD_ACCURACY + " REAL," +
			FIELD_TIME + " REAL," +
			FIELD_VERIFIED + " REAL " + ")";

	private static final String SQL_UPDATE_1_TO_2 = "ALTER TABLE " + TABLE_NAME + " ADD " + FIELD_VERIFIED + " REAL";

	public WifiLocationDatabase(Context context) {
		super(context, "wifiloc.db", null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(SQL_CREATE_TABLE);
	}

	public Location get(String mac) {
		Cursor cursor = getReadableDatabase().query(TABLE_NAME, null, FIELD_MAC + "=?", new String[]{mac}, null, null, null);
		if (cursor != null) {
			if (cursor.moveToNext()) {
				Location location = getLocation(cursor);
				cursor.close();
				return location;
			}
			cursor.close();
		}
		return null;
	}

	private Location getLocation(Cursor cursor) {
		Location location = new Location("database");
		Bundle extras = new Bundle();
		if (!cursor.isNull(cursor.getColumnIndexOrThrow(FIELD_MAC))) {
			extras.putString(LocationRetriever.EXTRA_MAC_ADDRESS, cursor.getString(cursor.getColumnIndex(FIELD_MAC)));
		}
		if (!cursor.isNull(cursor.getColumnIndexOrThrow(FIELD_LATITUDE))) {
			location.setLatitude(cursor.getDouble(cursor.getColumnIndex(FIELD_LATITUDE)));
		}
		if (!cursor.isNull(cursor.getColumnIndexOrThrow(FIELD_LONGITUDE))) {
			location.setLongitude(cursor.getDouble(cursor.getColumnIndex(FIELD_LONGITUDE)));
		}
		if (!cursor.isNull(cursor.getColumnIndexOrThrow(FIELD_ALTITUDE))) {
			location.setAltitude(cursor.getDouble(cursor.getColumnIndex(FIELD_ALTITUDE)));
		}
		if (!cursor.isNull(cursor.getColumnIndexOrThrow(FIELD_ACCURACY))) {
			location.setAccuracy(cursor.getFloat(cursor.getColumnIndex(FIELD_ACCURACY)));
		}
		if (!cursor.isNull(cursor.getColumnIndexOrThrow(FIELD_TIME))) {
			location.setTime(cursor.getLong(cursor.getColumnIndex(FIELD_TIME)));
		}
		if (!cursor.isNull(cursor.getColumnIndexOrThrow(FIELD_VERIFIED))) {
			extras.putLong(LocationRetriever.EXTRA_VERIFIED_TIME, cursor.getLong(cursor.getColumnIndex(FIELD_VERIFIED)));
		}
		location.setExtras(extras);
		return location;
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion == 1) {
			db.execSQL(SQL_UPDATE_1_TO_2);
			oldVersion = 2;
		}
		if (oldVersion != newVersion) {
			throw new RuntimeException("Upgrade not supported, sorry!");
		}
	}

	public List<Location> getNear(Location location, int limit) {
		// TODO: Pythagoras is wrong for LatLon...
		String order = "((" + FIELD_LATITUDE + "-(" + location.getLatitude() + "))*(" + FIELD_LATITUDE + "-(" + location.getLatitude() + "))+(" + FIELD_LONGITUDE + "-(" + location.getLongitude() + "))*(" + FIELD_LONGITUDE + "-(" + location.getLongitude() + ")))";
		Cursor cursor = getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, order, Integer.toString(limit));
		if (cursor != null) {
			List<Location> locations = new ArrayList<Location>();
			while (cursor.moveToNext()) {
				locations.add(getLocation(cursor));
			}
			cursor.close();
			return locations;
		}
		return null;
	}

	public Editor edit() {
		return new Editor();
	}

	public class Editor {
		private final SQLiteDatabase db;

		public Editor() {
			db = getWritableDatabase();
			db.beginTransaction();
		}

		public void put(Location location) {
			ContentValues values = new ContentValues();
			values.put(FIELD_MAC, location.getExtras().getString(LocationRetriever.EXTRA_MAC_ADDRESS));
			values.put(FIELD_LATITUDE, location.getLatitude());
			values.put(FIELD_LONGITUDE, location.getLongitude());
			if (location.hasAltitude()) {
				values.put(FIELD_ALTITUDE, location.getAltitude());
			}
			if (location.hasAccuracy()) {
				values.put(FIELD_ACCURACY, location.getAccuracy());
			}
			values.put(FIELD_TIME, location.getTime());
			values.put(FIELD_VERIFIED, location.getExtras().getLong(LocationRetriever.EXTRA_VERIFIED_TIME));
			db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
		}

		public void end() {
			db.setTransactionSuccessful();
			db.endTransaction();
		}
	}


}