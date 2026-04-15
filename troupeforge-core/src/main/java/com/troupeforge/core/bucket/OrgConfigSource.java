package com.troupeforge.core.bucket;

import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.OrganizationId;

import java.util.List;
import java.util.Optional;

public interface OrgConfigSource {
    OrganizationId organizationId();
    StageContext stage();
    List<String> listAgentDirectories(String parentPath);
    String readFile(String path);
    List<String> listFiles(String directory);
    Optional<String> configVersion();
}
