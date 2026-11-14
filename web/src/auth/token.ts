/** Realm roles from a Keycloak access token (realm_access.roles). Display only: the API decides. */
export function rolesFromToken(token: string): string[] {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(payload.length / 4) * 4, '='));
    const claims = JSON.parse(json) as { realm_access?: { roles?: unknown } };
    const roles = claims.realm_access?.roles;
    return Array.isArray(roles) ? roles.filter((r): r is string => typeof r === 'string') : [];
  } catch {
    return [];
  }
}
