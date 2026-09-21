package com.nullverse.nullkeyai.clipboard

class DeviceBoundProtectedImportException :
    IllegalArgumentException(
        "Device-bound protected clips cannot be restored from plain JSON. Use Secure Backup (.nkbackup)."
    )
