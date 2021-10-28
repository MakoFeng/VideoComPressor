package com.mako.videocompressor.utils;

import android.content.Context;

import java.io.File;

public class CacheUtils {
	
	public static String getVideoFilePath(Context context) {
		return getPath(context, "video");
	}

	private static String getPath(Context context, String dirName) {
		File cacheDir = context.getExternalCacheDir();
		if (cacheDir != null) {
			File result = new File(cacheDir, dirName);
			if (!result.mkdirs() && (!result.exists() || !result.isDirectory())) {
				return null;
			}
			return result.getAbsolutePath();
		}
		return "";
	}
	
}
