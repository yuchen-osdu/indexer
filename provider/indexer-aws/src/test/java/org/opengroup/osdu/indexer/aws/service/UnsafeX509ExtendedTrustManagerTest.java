/**
* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*      http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.opengroup.osdu.indexer.aws.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.springframework.boot.test.context.SpringBootTest;
import org.opengroup.osdu.indexer.aws.IndexerAwsApplication;
import java.net.Socket;

@RunWith(MockitoJUnitRunner.class)
@SpringBootTest(classes = {IndexerAwsApplication.class})
public class UnsafeX509ExtendedTrustManagerTest {
    

    @Test
    public void createClientBuilder() throws Exception {

        X509Certificate[] certificates = new X509Certificate[1];

        certificates[0] = new Certificate();
        
        UnsafeX509ExtendedTrustManager manager = UnsafeX509ExtendedTrustManager.INSTANCE;

        manager.checkClientTrusted(certificates, "auth");
        manager.checkClientTrusted(certificates, "auth", new Socket());
        manager.checkServerTrusted(certificates, "auth");
        manager.checkServerTrusted(certificates, "auth", new Socket());

    }

}

class Certificate extends X509Certificate{

    @Override
    public boolean hasUnsupportedCriticalExtension() {
        return true;
    }

    @Override
    public Set<String> getCriticalExtensionOIDs() {
        return new HashSet<String>();
    }

    @Override
    public Set<String> getNonCriticalExtensionOIDs() {
        return new HashSet<String>();
    }

    @Override
    public byte[] getExtensionValue(String oid) {
        return new byte[0];
    }

    @Override
    public void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException {
    }

    @Override
    public void checkValidity(Date date) throws CertificateExpiredException, CertificateNotYetValidException {
    }

    @Override
    public int getVersion() {
        return 0;
    }

    @Override
    public BigInteger getSerialNumber() {
        return new BigInteger("0");
    }

    @Override
    public Principal getIssuerDN() {
        return null;
    }

    @Override
    public Principal getSubjectDN() {
        return null;
    }

    @Override
    public X500Principal getSubjectX500Principal() {
        return null;
    }

    @Override
    public Date getNotBefore() {
        return null;
    }

    @Override
    public Date getNotAfter() {
        return null;
    }

    @Override
    public byte[] getTBSCertificate() throws CertificateEncodingException {
        return null;
    }

    @Override
    public byte[] getSignature() {
        return null;
    }

    @Override
    public String getSigAlgName() {
        return "";
    }

    @Override
    public String getSigAlgOID() {
        return "";
    }

    @Override
    public byte[] getSigAlgParams() {
        return null;
    }

    @Override
    public boolean[] getIssuerUniqueID() {
        return null;
    }

    @Override
    public boolean[] getSubjectUniqueID() {
        return null;
    }

    @Override
    public boolean[] getKeyUsage() {
        return null;
    }

    @Override
    public int getBasicConstraints() {
        return 0;
    }

    @Override
    public byte[] getEncoded() throws CertificateEncodingException {
        return null;
    }

    @Override
    public void verify(PublicKey key) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException,
            NoSuchProviderException, SignatureException {
    }

    @Override
    public void verify(PublicKey key, String sigProvider) throws CertificateException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException, SignatureException {
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public PublicKey getPublicKey() {
        return null;
    }
    
}