package com.mmlinaric.remotemanager.update;

import java.net.URI;

/** A published application version and the page where users can inspect its release notes. */
public record AppRelease(String version, URI page) {}
