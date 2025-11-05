"""
Gitee OAuth提供商实现
"""

from typing import Dict, Any
from ..base import OAuth2Provider
from ..exceptions import TokenError, UserInfoError


class GiteeProvider(OAuth2Provider):
    """Gitee OAuth 2.0提供商"""
    
    def get_provider_name(self) -> str:
        return "gitee"
    
    async def get_access_token(self, code: str) -> str:
        """获取Gitee访问令牌"""
        try:
            response = await self.handle_http_request(
                "POST",
                self.config["token_url"],
                data={
                    "client_id": self.config["client_id"],
                    "client_secret": self.config["client_secret"],
                    "code": code,
                    "grant_type": "authorization_code",
                    "redirect_uri": self.config["redirect_uri"]
                }
            )
            
            token_data = response.json()
            access_token = token_data.get("access_token")
            
            if not access_token:
                raise TokenError("Gitee未返回访问令牌", "no_token", "gitee")
            
            return access_token
            
        except Exception as e:
            if isinstance(e, TokenError):
                raise
            raise TokenError(f"获取Gitee访问令牌失败: {str(e)}", "token_request_failed", "gitee")
    
    async def get_user_info(self, access_token: str) -> Dict[str, Any]:
        """获取Gitee用户信息"""
        try:
            headers = {"Authorization": f"token {access_token}"}
            
            # 获取用户基本信息
            user_response = await self.handle_http_request(
                "GET",
                self.config["user_info_url"],
                headers=headers
            )
            user_info = user_response.json()
            
            # 获取用户邮箱信息
            user_email = ""
            try:
                emails_response = await self.handle_http_request(
                    "GET",
                    self.config["emails_url"],
                    headers=headers
                )
                
                if emails_response.status_code == 200:
                    emails_data = emails_response.json()
                    
                    # 优先选择主邮箱
                    primary_email = None
                    verified_email = None
                    
                    for email_info in emails_data:
                        if email_info.get("primary", False):
                            primary_email = email_info.get("email", "")
                        elif email_info.get("verified", False) and not verified_email:
                            verified_email = email_info.get("email", "")
                    
                    # 选择邮箱优先级：主邮箱 > 已验证邮箱 > 第一个邮箱
                    if primary_email:
                        user_email = primary_email
                    elif verified_email:
                        user_email = verified_email
                    elif emails_data and len(emails_data) > 0:
                        user_email = emails_data[0].get("email", "")
                        
            except Exception as e:
                import logging
                logger = logging.getLogger(__name__)
            
            # 检查邮箱收集需求
            processed_email, email_collection_needed = self.check_email_collection_needed(user_email)
            
            # 返回标准化用户信息
            return {
                "provider": "gitee",
                "uid": str(user_info.get("id", "")),
                "username": user_info.get("login", ""),
                "email": processed_email,
                "avatar": user_info.get("avatar_url", ""),
                "email_collection_needed": email_collection_needed
            }
            
        except Exception as e:
            if isinstance(e, UserInfoError):
                raise
            raise UserInfoError(f"获取Gitee用户信息失败: {str(e)}", "user_info_failed", "gitee")
