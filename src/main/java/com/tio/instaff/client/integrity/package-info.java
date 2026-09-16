/**
 * Client-side integrity verification subsystem.
 * Scans local mods/ and resourcepacks/ directories, computes SHA-256 hashes,
 * and reports them to the server via IntegrityResponsePayload.
 *
 * All classes in this package are CLIENT-ONLY and must never be imported by server code.
 */
package com.tio.instaff.client.integrity;