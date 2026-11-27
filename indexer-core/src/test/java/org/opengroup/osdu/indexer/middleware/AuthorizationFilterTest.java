package org.opengroup.osdu.indexer.middleware;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.entitlements.AuthorizationResponse;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.provider.interfaces.IAuthorizationService;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizationFilterTest {

    @Mock
    private IAuthorizationService authorizationService;

    @Mock
    private AuthorizationResponse authorizationResponse;

    @Mock
    private DpsHeaders headers;

    private AuthorizationFilter sut;

    @Before
    public void setUp() {
        sut = new AuthorizationFilter(headers);
        ReflectionTestUtils.setField(sut, "authorizationService", authorizationService);
    }

    @Test
    public void shouldAuthorizeWithInjectedRequestHeaders() {
        when(authorizationService.authorizeAny(headers, "required-role")).thenReturn(authorizationResponse);

        assertTrue(sut.hasPermission("required-role"));

        verify(authorizationService).authorizeAny(headers, "required-role");
    }
}
