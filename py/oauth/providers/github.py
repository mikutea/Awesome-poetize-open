"""
GitHub OAuth提供商实现
"""

from typing import Dict, Any
import httpx
from ..base import OAuth2Provider
from ..exceptions import TokenError, UserInfoError


class GitHubProvider(OAuth2Provider):
    """GitHub OAuth 2.0提供商"""
    
    def get_provider_name(self) -> str:
        return "github"
    
    async def get_access_token(self, code: str) -> str:
        """获取GitHub访问令牌"""
        try:
            response = await self.handle_http_request(
                "POST",
                self.config["token_url"],
                headers={"Accept": "application/json"},
                data={
                    "client_id": self.config["client_id"],
                    "client_secret": self.config["client_secret"],
                    "code": code,
                    "redirect_uri": self.config["redirect_uri"]
                }
            )
            
            token_data = response.json()
            access_token = token_data.get("access_token")
            
            if not access_token:
                raise TokenError("未返回访问令牌", "no_token", "github")
            
            return access_token
            
        except Exception as e:
            if isinstance(e, TokenError):
                raise
            raise TokenError(f"获取访问令牌失败: {str(e)}", "token_request_failed", "github")
    
    async def get_user_info(self, access_token: str) -> Dict[str, Any]:
        """获取GitHub用户信息"""
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
            emails_response = await self.handle_http_request(
                "GET", 
                self.config["emails_url"],
                headers=headers
            )
            emails = emails_response.json()
            
            # 查找主邮箱
            primary_email = next(
                (e["email"] for e in emails if e["primary"] and e["verified"]), 
                None
            )
            
            # 检查邮箱收集需求
            processed_email, email_collection_needed = self.check_email_collection_needed(primary_email)
            
            # 返回标准化用户信息
            return {
                "provider": "github",
                "uid": str(user_info.get("id", "")),
                "username": user_info.get("login", ""),
                "email": processed_email,
                "avatar": user_info.get("avatar_url", ""),
                "email_collection_needed": email_collection_needed
            }
            
        except Exception as e:
            if isinstance(e, UserInfoError):
                raise
            raise UserInfoError(f"获取用户信息失败: {str(e)}", "user_info_failed", "github")
