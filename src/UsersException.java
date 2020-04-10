

import javax.ws.rs.core.Response.Status;
import javax.xml.ws.WebFault;

@WebFault
public class UsersException extends Exception
{

	private static final long serialVersionUID = 1L;

	public UsersException() {
		super("");
	}

	public UsersException(String errorMessage ) {
		super(errorMessage);
	}
	
	public UsersException(Status errorStatus)
	{
		super(errorStatus.toString());
	}
}
