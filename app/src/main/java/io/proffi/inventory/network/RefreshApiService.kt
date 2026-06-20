package io.proffi.inventory.network

/**
 * Wrapper around a bare [ApiService] (no auth interceptor / authenticator) used
 * exclusively for the token-refresh call. Keeping it separate prevents the
 * refresh request from re-entering [TokenAuthenticator] and looping on 401.
 */
class RefreshApiService(val api: ApiService)
