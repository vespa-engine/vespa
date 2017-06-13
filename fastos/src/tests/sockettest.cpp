// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tests.h"
#include <vespa/fastos/file.h>
#include <vespa/fastos/serversocket.h>


#define MAZE_FILE_OFFSET   1078

#define MAZE_WIDTH         776
#define MAZE_HEIGHT        483
#define MAZE_START_X       3
#define MAZE_START_Y       399
#define MAZE_END_X         759
#define MAZE_END_Y         63

#define MAZE_WALL          0
#define MAZE_EXIT_LEFT     1
#define MAZE_EXIT_RIGHT    2

#define MAZE_DIRECTION_EAST   0
#define MAZE_DIRECTION_SOUTH  1
#define MAZE_DIRECTION_WEST   2
#define MAZE_DIRECTION_NORTH  3

#define MAZE_QUERY_HALLWAY    1234
#define MAZE_QUERY_QUIT       1235


#define MAX_CONNECTIONS 20

#define SEARCHBUF_SIZE (1024*1024/(sizeof(u_short)))


class MazeServices
{
public:
   int TurnLeft (int direction)
   {
      direction--;
      if(direction == -1)
         direction = 3;
      return direction;
   }

   int TurnRight (int direction)
   {
      return (direction+1) % 4;
   }

   void SetDirection(int &dx, int &dy, int direction)
   {
      switch(direction)
      {
         case MAZE_DIRECTION_EAST:
            dx = 2;
            dy = 0;
            break;

         case MAZE_DIRECTION_SOUTH:
            dx = 0;
            dy = 2;
            break;

         case MAZE_DIRECTION_WEST:
            dx = -2;
            dy = 0;
            break;

         case MAZE_DIRECTION_NORTH:
            dx = 0;
            dy = -2;
            break;
      }
   }
   virtual ~MazeServices(void) { }
};

class MazeServer;


#define BUFFER_SIZE 20000
class MazeServerConnection
{
private:
   MazeServerConnection(const MazeServerConnection&);
   MazeServerConnection& operator=(const MazeServerConnection&);

   MazeServer *_server;
   u_short _receiveBuffer[BUFFER_SIZE/sizeof(u_short)];
   u_short _sendBuffer[BUFFER_SIZE/sizeof(u_short)];
   unsigned char *_sendPtr, *_receivePtr;
   int _bytesToSend;

   int ReceiveBufferSpace ()
   {
      return static_cast<int>
          (reinterpret_cast<unsigned char *>(_receiveBuffer + BUFFER_SIZE) -
           _receivePtr);
   }

   int SendBufferSpace ()
   {
      return static_cast<int>
          (reinterpret_cast<unsigned char *>(&_sendBuffer[BUFFER_SIZE]) -
           _sendPtr);
   }

   unsigned int ReceiveBufferBytes ()
   {
      return BUFFER_SIZE - ReceiveBufferSpace();
   }

   unsigned int SendBufferBytes ()
   {
      return BUFFER_SIZE - SendBufferSpace();
   }

public:
   FastOS_Socket *_socket;
   bool _shouldFree;

   MazeServerConnection (MazeServer *server, FastOS_Socket *sock)
     : _server(server),
       _sendPtr(reinterpret_cast<unsigned char *>(_sendBuffer)),
       _receivePtr(reinterpret_cast<unsigned char *>(_receiveBuffer)),
       _bytesToSend(0),
      _socket(sock),
       _shouldFree(false)
   {
   }

   bool ReadEvent ();
   bool WriteEvent ();
};


class MazeServer : public MazeServices
{
private:
   MazeServer(const MazeServer&);
   MazeServer& operator=(const MazeServer&);

public:
   unsigned char _mazeBitmap[MAZE_HEIGHT][MAZE_WIDTH];
   unsigned char _mazeBitmap2[MAZE_HEIGHT][MAZE_WIDTH];

   FastOS_ServerSocket *_serverSocket;
   BaseTest *_app;

   MazeServer(BaseTest *app)
     : _serverSocket(NULL),
       _app(app)
   {
   }

   bool Initialize()
   {
      bool rc;
      const char *filename = "mazebitmap.bmp";

      FastOS_File file;

      rc = file.OpenReadOnly(filename);

      _app->Progress(rc, "Opening maze bitmap (%s)", filename);

      if(rc)
      {
         rc = file.SetPosition(MAZE_FILE_OFFSET);

         _app->Progress(rc, "Setting file position (%d)", MAZE_FILE_OFFSET);

         if(rc)
         {
            int readBytes = file.Read(_mazeBitmap, MAZE_WIDTH * MAZE_HEIGHT);

            rc = (readBytes == MAZE_WIDTH*MAZE_HEIGHT);

            _app->Progress(rc, "Reading %d bytes from '%s'",
               MAZE_WIDTH*MAZE_HEIGHT, filename);

            if(rc)
            {
               int serverPort = 18334;
               _serverSocket = new FastOS_ServerSocket(serverPort);
               _app->Progress(_serverSocket != NULL,
                              "Creating ServerSocket instance");

               _app->Progress(_serverSocket->SetSoBlocking(false),
                              "Set non-blocking");

               _app->Progress(_serverSocket->Listen(),
                              "Bind socket to port %d. Listen for "
                              "incoming connections.", serverPort);
            }
         }
      }

      return rc;
   }

   void Run ()
   {
      if(Initialize())
      {
         MazeServerConnection *connections[MAX_CONNECTIONS], *conn;
         FastOS_SocketEvent socketEvent;
         int i;

         memset(connections, 0, sizeof(connections));

         _serverSocket->SetSocketEvent(&socketEvent);
         _serverSocket->EnableReadEvent(true);

         for(;;)
         {
            bool waitError=false;

            if(socketEvent.Wait(waitError, 200))
            {
               if(socketEvent.QueryReadEvent(_serverSocket))
               {
                  FastOS_Socket *connSocket = _serverSocket->AcceptPlain();

                  _app->Progress(connSocket != NULL, "Accepting socket (%p)",
                                 connSocket);

                  for(i=0; i<MAX_CONNECTIONS; i++)
                  {
                     if(connections[i] == NULL)
                     {
                        // Found a free positions for the new connection
                        break;
                     }
                  }
                  if(i < MAX_CONNECTIONS)
                  {
                     if(connSocket != NULL)
                     {
                        connections[i] = new MazeServerConnection(this, connSocket);

                        assert(connections[i] != NULL);

                        connSocket->SetSocketEvent(&socketEvent);
                        connSocket->EnableReadEvent(true);
                     }
                  }
                  else
                  {
                     _app->Progress(false, "Rejecting connection. Only %d allowed.", MAX_CONNECTIONS);
                     delete(connSocket);
                  }
               }

               for(i=0; i<MAX_CONNECTIONS; i++)
               {
                  if((conn = connections[i]) != NULL)
                  {
                     if(socketEvent.QueryReadEvent(conn->_socket))
                     {
                        if(conn->ReadEvent())
                           conn->_socket->EnableWriteEvent(true);
                     }

                     if(socketEvent.QueryWriteEvent(conn->_socket))
                     {
                        if(!conn->WriteEvent())
                           conn->_socket->EnableWriteEvent(false);
                     }

                     if(conn->_shouldFree)
                     {
                        delete(conn);
                        connections[i] = NULL;
                     }
                  }
               }
            }
         }
      }
   }

   int Read (int x, int y, int direction, u_short *p)
   {
      int leftDx = 0, leftDy = 0, rightDx = 0, rightDy = 0;
      int forwardDx = 0, forwardDy = 0;

      int entries=0;
      int distance=0;

      SetDirection(forwardDx, forwardDy, direction);
      SetDirection(leftDx, leftDy, TurnLeft(direction));
      SetDirection(rightDx, rightDy, TurnRight(direction));

      u_short *numEntries = p;
      p++;

      for(;;)
      {
         x += forwardDx;
         y += forwardDy;

         distance++;

         if(_mazeBitmap[MAZE_HEIGHT-1-(y)][x] == 0)      // Did we run into wall?
         {
            *p++ = htons(MAZE_WALL);
            *p++ = htons(distance);
            entries++;
            break;
         }

         if(_mazeBitmap[MAZE_HEIGHT-1-(y+leftDy)][x+leftDx] != 0)
         {
            *p++ = htons(MAZE_EXIT_LEFT);
            *p++ = htons(distance);
            distance=0;
            entries++;
         }

         if(_mazeBitmap[MAZE_HEIGHT-1-(y+rightDy)][x+rightDx] != 0)
         {
            *p++ = htons(MAZE_EXIT_RIGHT);
            *p++ = htons(distance);
            distance=0;
            entries++;
         }
      }

      *numEntries = htons(entries);

      return sizeof(u_short) * ((entries*2)+1);
   }
};



bool MazeServerConnection::ReadEvent ()
{
   bool startSending=false;

   int bytesRead = _socket->Read(_receivePtr, ReceiveBufferSpace());

   if(bytesRead > 0)
   {
      _receivePtr = &_receivePtr[bytesRead];

      if(ReceiveBufferBytes() >= (sizeof(u_short)*4))
      {
         _receivePtr = reinterpret_cast<unsigned char *>(_receiveBuffer);
         u_short *p = _receiveBuffer;

         if(ntohs(p[0]) == MAZE_QUERY_HALLWAY)
         {
            int x = ntohs(p[1]);
            int y = ntohs(p[2]);
            int direction = ntohs(p[3]);

            _sendPtr = reinterpret_cast<unsigned char *>(_sendBuffer);
            _bytesToSend = _server->Read(x, y, direction,
                    static_cast<u_short *>(_sendBuffer));

            startSending = true;
         }
      }
   }
   else
   {
      _shouldFree = true;
      _server->_app->Progress(true, "Closing connection");
   }

   return startSending;
}

bool MazeServerConnection::WriteEvent ()
{
   bool sendMoreLater=false;

   if(_bytesToSend > 0)
   {
      int bytesWritten= _socket->Write(_sendPtr, _bytesToSend);

      if(bytesWritten > 0)
      {
         _bytesToSend -= bytesWritten;
         _sendPtr = &_sendPtr[bytesWritten];
      }
      else
      {
         _server->_app->Progress(false,
            "Error writing %d bytes to socket", _bytesToSend);
      }

      sendMoreLater = _bytesToSend > 0;
   }

   return sendMoreLater;
}




class MazeClient : public MazeServices
{
private:
   MazeClient(const MazeClient&);
   MazeClient& operator=(const MazeClient&);

   bool _visitedPoints[MAZE_WIDTH][MAZE_HEIGHT];
   u_short _searchTreeBuffer[SEARCHBUF_SIZE];
   u_short *_bufferPosition;
   bool _foundExit;
   FastOS_Socket *_sock;
   BaseTest *_app;

public:
   MazeClient(BaseTest *app, FastOS_Socket *sock)
     : MazeServices(),
       _bufferPosition(_searchTreeBuffer),
       _foundExit(false),
       _sock(sock),
       _app(app)
   {
      memset(_visitedPoints, 0, sizeof(_visitedPoints));
   }

   void Run ()
   {
      int x = MAZE_START_X;
      int y = MAZE_START_Y;

      Search(x, y, 1);
   }

#if 0
   void PrintOut()
   {
      if(_server != NULL)
      {
         for(int y=0; y<MAZE_HEIGHT; y++)
         {
            for(int x=0; x<MAZE_WIDTH; x++)
            {
               if(_server->_mazeBitmap[MAZE_HEIGHT-1-y][x] == 0)
                  printf("*");
               else if(_server->_mazeBitmap2[MAZE_HEIGHT-1-y][x] == 200)
                  printf("X");
               else
                  printf("%c", _server->_mazeBitmap2[MAZE_HEIGHT-1-y][x] == 50 ? '.' : ' ');
            }
            printf("\n");
         }
      }
   }
#endif

#if 0
   void MarkPath (int x, int y, int direction, int length)
   {
      if(_server != NULL)
      {
         int dx, dy;
         SetDirection(dx, dy, direction);

         while(length > 0)
         {
            x += dx;
            y += dy;

            _server->_mazeBitmap2[MAZE_HEIGHT-1-y][x] = 200;
            length--;
         }
      }
   }
#endif

   bool Move (int &x, int &y, int direction, int length)
   {
      int dx = 0, dy = 0;

      int startx = x;
      int starty = y;

      SetDirection(dx, dy, direction);

      bool continueAfterMove=true;

      while(length > 0)
      {
         x += dx;
         y += dy;

         if((x == MAZE_END_X) && (y == MAZE_END_Y))
         {
            _app->Progress(true, "Found exit at (%d, %d).", x, y);
            _foundExit = true;
            continueAfterMove = false;
            break;
         }

         if(_visitedPoints[x][y])
         {
            continueAfterMove = false;
            break;
         }
         else
         {
            _visitedPoints[x][y] = true;
         }
         length--;
      }

      if(!continueAfterMove)
      {
         x = startx;
         y = starty;
      }

      return continueAfterMove;
   }

   int ReadFromServer (int x, int y, int direction, u_short *p)
   {
      int readItems=0;


      u_short *writePtr=p;

      *writePtr++ = htons(MAZE_QUERY_HALLWAY);
      *writePtr++ = htons(static_cast<u_short>(x));
      *writePtr++ = htons(static_cast<u_short>(y));
      *writePtr++ = htons(static_cast<u_short>(direction));

      int sendBytes = static_cast<int>
                      (reinterpret_cast<char *>(writePtr) -
                       reinterpret_cast<char *>(p));

      int actualSent = _sock->Write(p, sendBytes);

      if(actualSent != sendBytes)
      {
         _app->Progress(false, "Sending %d bytes to maze server (rc=%d)",
                        sendBytes, actualSent);
      }
      else
      {
         int actualRead = _sock->Read(p, sizeof(u_short));

         if(actualRead != sizeof(u_short))
         {
            _app->Progress(false, "Reading %d bytes from maze server (rc=%d)",
                           sizeof(u_short), actualRead);
         }
         else
         {
            int packetSize = ntohs(*p);
            p++;
            int readBytes = packetSize * 2 * sizeof(u_short);

            actualRead = _sock->Read(p, readBytes);

            if(actualRead != readBytes)
            {
               _app->Progress(false, "Reading %d bytes from maze server (rc=%d)",
                              readBytes, actualRead);
            }

            readItems = 1 + actualRead/sizeof(u_short);
         }
      }

      return readItems;
   }

   void Search (int startX, int startY, int direction);
};


void MazeClient::Search (int startX, int startY, int direction)
{
   u_short *p = _bufferPosition;

   int readEntries = ReadFromServer(startX, startY, direction, p);

   p++;

   _bufferPosition = &_bufferPosition[readEntries];
   assert(_bufferPosition < &_searchTreeBuffer[SEARCHBUF_SIZE]);

   bool continueSearching = true;

   int x=startX;
   int y=startY;
   int length=0;

   while(continueSearching)
   {
      u_short code = ntohs(*p++);
      u_short distance = ntohs(*p++);

      switch(code)
      {
         case MAZE_WALL:
         {
            distance--;
            // Make sure we have visited all the points
            Move(x, y, direction, distance);
            continueSearching = false;
            break;
         }

         case MAZE_EXIT_LEFT:
         {
            if(Move(x, y, direction, distance))
               Search(x, y, TurnLeft(direction));
            break;
         }

         case MAZE_EXIT_RIGHT:
         {
            if(Move(x, y, direction, distance))
               Search(x, y, TurnRight(direction));
            break;
         }

      default:
         {
            _app->Progress(false, "Unknown maze code (%d, %d) in packet", code, distance);
            continueSearching = false;
            break;
         }
      }

      length += distance;

      if(_foundExit)
      {
         continueSearching = false;
      }
   }
}


class SocketTest : public BaseTest
{
public:
   void StrictBindTest ()
   {
      bool rc;

      TestHeader("Strict Bind Test");

      // Fallback to localhost if we can't get the hostname
      std::string strictBindHost("localhost");

      FastOS_ServerSocket *serverSocket =
         new FastOS_ServerSocket(18333, 5, NULL, strictBindHost.c_str());

      Progress(serverSocket != NULL, "Allocating serversocket instance");

      rc = serverSocket->GetValidAddressFlag();
      Progress(rc, "Address Valid Flag check");

      if(rc)
      {
         Progress(rc = serverSocket->Listen(),
                  "Strict bind socket to %s on port %d. Listen "
                  "for incoming connections.", strictBindHost.c_str(), 18333);
      }

      delete(serverSocket);
      Progress(true, "Deleted serversocket");

      PrintSeparator();
   }

   void HttpClientTest ()
   {
      bool rc=false;

      TestHeader("HTTP Client Test");

      FastOS_Socket *sock = new FastOS_Socket();
      Progress(sock != NULL, "Allocating socket instance");

      char hostAddress[] = "www.vg.no";

      rc = sock->SetAddress(80, hostAddress);
      Progress(rc, "Setting hostAddress (%s)", hostAddress);

      if(rc)
        Progress(rc = sock->Connect(), "Connecting to %s", hostAddress);

      if(rc)
      {
         int localPort = sock->GetLocalPort();

         Progress(localPort != -1, "Localport = %d", localPort);

         char sendCommand[] = "GET / HTTP/1.1\r\nHost: www.vg.no\r\n\r\n";
         int sendLength = strlen(sendCommand)+1;

         int wroteBytes = sock->Write(sendCommand, sendLength);
         Progress(rc = (wroteBytes == sendLength),
                  "Write %d bytes to socket (GET / HTTP/1.1 ...)", wroteBytes);

         if(rc)
         {
            char expectedResult[] = "HTTP/1.X 200 Ok";

            int readLength = strlen(expectedResult);
            char *readBuffer = new char[readLength+1];

            if(readBuffer != NULL)
            {
               memset(readBuffer, 0, readLength+1);

               int actualRead = sock->Read(readBuffer, readLength);
               Progress(rc = (actualRead == readLength), "Read %d bytes from socket", actualRead);
               Progress(true, "Contents: [%s]", readBuffer);

               expectedResult[7] = '0';
               rc = (strcasecmp(expectedResult, readBuffer) == 0);
               expectedResult[7] = '1';
               rc |= (strcasecmp(expectedResult, readBuffer) == 0);
               expectedResult[7] = '2';
               rc |= (strcasecmp(expectedResult, readBuffer) == 0);

               expectedResult[7] = 'X';

               Progress(rc, "Comparing read result to expected result (%s)", expectedResult);

               delete [] readBuffer;
            }
            else
               Fail("Allocating read buffer");
         }

         Progress(sock->Shutdown(), "Socket shutdown");
         Progress(rc = sock->Close(), "Closing socket");
      }

      delete(sock);
      Progress(true, "Deleted socket");

      PrintSeparator();
   }

   void ClientServerTest ()
   {
      bool rc;

      TestHeader("Client/Server Test");

      FastOS_ServerSocket *serverSocket = new FastOS_ServerSocket(18333);
      Progress(serverSocket != NULL, "Allocating serversocket instance");

      Progress(rc = serverSocket->Listen(), "Bind socket to port %d. Listen for incoming connections.", 18333);
      assert(rc);

      delete(serverSocket);
      Progress(true, "Deleted serversocket");

      PrintSeparator();
   }


   void MazeTest (char *serverAddress)
   {
      TestHeader("Maze Test");

      bool rc;
      FastOS_Socket *sock = new FastOS_Socket();

      Progress(rc = (sock != NULL), "Allocating socket instance");
      if(rc)
      {
         sock->SetAddress(8001, serverAddress);
         Progress(true, "Setting hostAddress (%s)", serverAddress);

         Progress(rc = sock->Connect(), "Connecting to %s", serverAddress);
         if(rc)
         {
            MazeClient *client = new MazeClient(this, sock);

            Progress(rc = (client != NULL), "Allocating MazeClient instance");
            if(rc)
            {
               client->Run();
               delete(client);
            }
         }

         delete(sock);
      }

      PrintSeparator();
   }

   void DoMazeServer ()
   {
      TestHeader("Maze Server");

      MazeServer *server = new MazeServer(this);
      server->Run();

      PrintSeparator();
   }

   int Main () override
   {
      printf("This test should be run in the 'test/workarea' directory.\n\n");
      printf("grep for the string '%s' to detect failures.\n\n", failString);

#if DO_MAZE_SERVER
      DoMazeServer();
#else
      char *mazeServerAddress = NULL;

      if(_argc == 3)
      {
         if(strcmp("/mazeserver", _argv[1]) == 0)
         {
            mazeServerAddress = _argv[2];
         }
      }

      HttpClientTest();
      ClientServerTest();
      StrictBindTest();

      if(mazeServerAddress != NULL)
         MazeTest(mazeServerAddress);
#endif


      PrintSeparator();

      printf("END OF TEST (%s)\n", _argv[0]);

      return 0;
   }
};


int main (int argc, char **argv)
{
   SocketTest app;

   setvbuf(stdout, NULL, _IOLBF, 8192);
   return app.Entry(argc, argv);
}
