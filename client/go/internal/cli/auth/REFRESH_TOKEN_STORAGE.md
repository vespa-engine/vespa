# refresh token storage

how the vespa CLI stores and retrieves OAuth2 refresh tokens, and the
fallback mechanism for systems where the OS keyring is unavailable.

## overview

the vespa CLI uses a two-token OAuth2 flow:

- **access token** — short-lived (30 min), stored in `~/.vespa/auth.json`
- **refresh token** — long-lived (24 hours), stored in the system keyring or a local file

when the access token expires, the CLI uses the refresh token to obtain a
new one without requiring the user to re-authenticate in the browser.

## storage backends

the CLI has two `SecretStore` implementations for persisting the refresh token:

| backend | type | storage location | when used |
|---|---|---|---|
| system keyring | `realKeyring` | macOS Keychain, GNOME Keyring, Windows Credential Manager | default (primary) |
| file storage | `dummyKeyring` | `~/.vespa/keyring.<namespace>.<key>` (mode 0o400) | fallback, or `--file-storage` / `VESPA_CLI_DUMMY_KEYRING=1` |

## fallback keyring

`NewKeyring()` returns a `fallbackKeyring` that composes both backends:

```
┌─────────────────────────────────────────┐
│           fallbackKeyring               │
│                                         │
│  Set(k, v):                             │
│    try realKeyring.Set(k, v)            │
│    if err → dummyKeyring.Set(k, v)      │
│                                         │
│  Get(k):                                │
│    try realKeyring.Get(k)               │
│    if err or empty → dummyKeyring.Get(k)│
│                                         │
│  Delete(k):                             │
│    realKeyring.Delete(k)                │
│    dummyKeyring.Delete(k)               │
│    fail only if both fail               │
└─────────────────────────────────────────┘
```

this means:

- on systems with a working keyring, behavior is unchanged
- on systems where the keyring fails (unsigned binaries, SSH, Docker, CI),
  the token is transparently stored in `~/.vespa/` files
- the refresh path (`AccessToken()` in `auth0.go`) finds the token
  regardless of which backend stored it

## explicit overrides

users can bypass the fallback and force file-only storage:

- **`--file-storage` flag** on `vespa auth login` — uses `dummyKeyring` directly
- **`VESPA_CLI_DUMMY_KEYRING=1`** environment variable — makes all
  `NewKeyring()` and `NewKeyringWithOptions(false)` calls return `dummyKeyring`

both skip the system keyring entirely, which avoids the keyring failure
latency on systems known to lack keyring support.

## token lifecycle

```
vespa auth login
  │
  ├─ browser OAuth2 device flow → access token + refresh token
  │
  ├─ store refresh token:
  │    NewKeyringWithOptions(useFileStorage).Set("vespa-cli", system, refreshToken)
  │    └─ without --file-storage: fallbackKeyring tries keyring, then file
  │    └─ with --file-storage: dummyKeyring writes file directly
  │
  └─ store access token + expiry → ~/.vespa/auth.json

(later, any vespa command)
  │
  ├─ read access token from ~/.vespa/auth.json
  ├─ if expired (within 5 min of expiry):
  │    NewKeyring().Get("vespa-cli", system)  → retrieve refresh token
  │    └─ fallbackKeyring: tries keyring, then file
  │    POST refresh token to OAuth token endpoint → new access token
  │    update ~/.vespa/auth.json
  └─ use access token in Authorization header
```

## file permissions

the file-based token (`~/.vespa/keyring.vespa-cli.<system>`) is created
with mode `0o400` (owner read-only). before overwriting, the existing file
is removed first — `os.WriteFile` cannot truncate a read-only file, so
`os.Remove` is called before each write.

the `~/.vespa/` directory is created with mode `0o700` (owner only).

## security considerations

the file-based token is stored unencrypted. anyone with read access to
`~/.vespa/keyring.vespa-cli.*` can use the refresh token to generate new
access tokens for its remaining lifetime (up to 24 hours). this is
comparable to SSH private keys or cloud CLI credentials stored in
`~/.config/`.

the system keyring provides better protection (encrypted at rest, access
gated by OS authentication), which is why it remains the primary backend.
file storage is a fallback for environments where the keyring is not
available.
