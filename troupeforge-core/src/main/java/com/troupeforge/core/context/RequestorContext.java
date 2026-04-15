package com.troupeforge.core.context;

import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.id.UserId;

public record RequestorContext(UserId userId, OrganizationId organizationId) {
}
