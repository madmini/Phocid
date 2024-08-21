package org.sunsetware.phocid

import java.io.File
import org.apache.commons.io.FilenameUtils
import org.junit.Test
import org.sunsetware.phocid.data.listDependencies

class DependencyLicenseTest {
    @Test
    fun noMissingLicenseText() {
        listDependencies { File(FilenameUtils.concat("src/main/assets", it)).readText() }
    }
}
