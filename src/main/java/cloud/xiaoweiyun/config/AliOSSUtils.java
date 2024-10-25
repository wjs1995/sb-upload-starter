package cloud.xiaoweiyun.config;

import cloud.xiaoweiyun.dto.StsTokenVo;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.EnvironmentVariableCredentialsProvider;
import com.aliyun.oss.model.PutObjectResult;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;


public class AliOSSUtils {
    public AliOSSProperties getAliOSSProperties() {
        return aliOSSProperties;
    }

    public void setAliOSSProperties(AliOSSProperties aliOSSProperties) {
        this.aliOSSProperties = aliOSSProperties;
    }

    private AliOSSProperties aliOSSProperties;


    public String upload(MultipartFile file) throws IOException, ClientException {
        String endpoint = aliOSSProperties.getEndpoint();
        String bucketName = aliOSSProperties.getBucketName();
        InputStream inputStream = file.getInputStream();
        String originalFilename = file.getOriginalFilename();
        String newFileName = UUID.randomUUID() +originalFilename.substring(originalFilename.lastIndexOf('.'));

        EnvironmentVariableCredentialsProvider credentialsProvider = CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();
        OSS ossClient = new OSSClientBuilder().build(endpoint,credentialsProvider);
        try {
            PutObjectResult putObjectResult = ossClient.putObject(bucketName, newFileName, inputStream);
            String url = endpoint.split("//")[0] + "//" + bucketName + "." + endpoint.split("//")[1] + "/" + newFileName;
            return url;
        } catch (OSSException oe) {
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } catch (com.aliyun.oss.ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
        return "";
    }

    public StsTokenVo getStsToken() {
        String endpoint = aliOSSProperties.getStsEndpoint();
        // STS服务接入点，例如sts.cn-hangzhou.aliyuncs.com。您可以通过公网或者VPC接入STS服务。
//        String endpoint = "sts.cn-hangzhou.aliyuncs.com";
        // 从环境变量中获取步骤1生成的RAM用户的访问密钥（AccessKey ID和AccessKey Secret）。
        String accessKeyId = System.getenv("OSS_ACCESS_KEY_ID");
        String accessKeySecret = System.getenv("OSS_ACCESS_KEY_SECRET");
        // 从环境变量中获取步骤3生成的RAM角色的RamRoleArn。
        String roleArn = System.getenv("OSS_STS_ROLE_ARN");

        // 自定义角色会话名称，用来区分不同的令牌，例如可填写为SessionTest。
        String roleSessionName = "TemporaryRole";
        // 临时访问凭证将获得角色拥有的所有权限。
        String policy = null;
        // 临时访问凭证的有效时间，单位为秒。最小值为900，最大值以当前角色设定的最大会话时间为准。当前角色最大会话时间取值范围为3600秒~43200秒，默认值为3600秒。
        // 在上传大文件或者其他较耗时的使用场景中，建议合理设置临时访问凭证的有效时间，确保在完成目标任务前无需反复调用STS服务以获取临时访问凭证。
        Long durationSeconds = 3600L;
        try {
            // 发起STS请求所在的地域。建议保留默认值，默认值为空字符串（""）。
            String regionId = "";
            // 添加endpoint。适用于Java SDK 3.12.0及以上版本。
            DefaultProfile.addEndpoint(regionId, "Sts", endpoint);
            // 添加endpoint。适用于Java SDK 3.12.0以下版本。
            // DefaultProfile.addEndpoint("",regionId, "Sts", endpoint);
            // 构造default profile。
            IClientProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret);
            // 构造client。
            DefaultAcsClient client = new DefaultAcsClient(profile);
            final AssumeRoleRequest request = new AssumeRoleRequest();
            // 适用于Java SDK 3.12.0及以上版本。
            request.setSysMethod(MethodType.POST);
            // 适用于Java SDK 3.12.0以下版本。
            // request.setMethod(MethodType.POST);
            request.setRoleArn(roleArn);
            request.setRoleSessionName(roleSessionName);
            request.setPolicy(null);
            request.setDurationSeconds(durationSeconds);
            final AssumeRoleResponse response = client.getAcsResponse(request);

            StsTokenVo vo = new StsTokenVo();
            vo.setAccessKeyId(response.getCredentials().getAccessKeyId());
            vo.setExpiration(response.getCredentials().getExpiration());
            vo.setSecurityToken(response.getCredentials().getSecurityToken());
            vo.setAccessKeySecret(response.getCredentials().getAccessKeySecret());
            vo.setRequestId(response.getRequestId());
            return vo;
        } catch (ClientException e) {
            System.out.println("Failed：");
            System.out.println("Error code: " + e.getErrCode());
            System.out.println("Error message: " + e.getErrMsg());
            System.out.println("RequestId: " + e.getRequestId());
            return null;
        }
    }
}
